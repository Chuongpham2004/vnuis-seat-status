// Seat node firmware: ESP32 DevKit V1 + HC-SR04 -> MQTT (EMQX).
//
// Publishes (see docs/mqtt-contract.md):
//   library/zone/{ZONE}/seat/{SEAT}/telemetry  on every debounced change + every 30 s
//   library/zone/{ZONE}/seat/{SEAT}/status     retained "ONLINE" on connect,
//                                              retained "OFFLINE" as Last Will

#include <Arduino.h>
#include <ArduinoJson.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <time.h>

#if __has_include("config.h")
#include "config.h"
#else
#warning "include/config.h not found - building with config.example.h"
#include "config.example.h"
#endif

#include "PresenceFilter.h"
#include "UltrasonicSensor.h"

namespace {

const String TOPIC_BASE = String("library/zone/") + ZONE_CODE + "/seat/" + SEAT_CODE;
const String TOPIC_TELEMETRY = TOPIC_BASE + "/telemetry";
const String TOPIC_STATUS = TOPIC_BASE + "/status";
const char *WILL_PAYLOAD = "{\"deviceId\":\"" DEVICE_ID "\",\"status\":\"OFFLINE\"}";

WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);
UltrasonicSensor sensor(TRIG_PIN, ECHO_PIN, NO_ECHO_DISTANCE_CM);
PresenceFilter filter(THRESHOLD_CM, DEBOUNCE_MS, MOVING_AVG_WINDOW, MAX_RANGE_CM);

uint32_t lastSampleMs = 0;
uint32_t lastTelemetryMs = 0;
uint32_t lastWifiAttemptMs = 0;
uint32_t lastMqttAttemptMs = 0;
uint32_t mqttBackoffMs = 1000;           // 1 s, doubling up to 30 s
const uint32_t MQTT_BACKOFF_MAX_MS = 30000;
const uint32_t WIFI_RETRY_MS = 5000;
bool wifiWasConnected = false;

uint64_t epochMillis() {
    time_t now = time(nullptr);
    if (now < 1700000000) {              // NTP not synced yet
        return 0;
    }
    return (uint64_t)now * 1000ULL + (millis() % 1000);
}

bool publishStatus(const char *status) {
    JsonDocument doc;
    doc["deviceId"] = DEVICE_ID;
    doc["status"] = status;
    uint64_t ts = epochMillis();
    if (ts) {
        doc["ts"] = ts;
    }
    char buf[128];
    size_t n = serializeJson(doc, buf, sizeof(buf));
    return mqtt.publish(TOPIC_STATUS.c_str(), (const uint8_t *)buf, n, true); // retained
}

bool publishTelemetry(const char *reason) {
    if (!mqtt.connected()) {
        return false;
    }
    JsonDocument doc;
    doc["deviceId"] = DEVICE_ID;
    doc["seatCode"] = SEAT_CODE;
    doc["distanceCm"] = roundf(filter.averageCm() * 10.0f) / 10.0f;
    doc["objectDetected"] = filter.isPresent();
    doc["rssi"] = WiFi.RSSI();
    doc["simulated"] = false;
    doc["fw"] = FW_VERSION;
    doc["uptimeMs"] = millis();
    uint64_t ts = epochMillis();
    if (ts) {
        doc["ts"] = ts;
    }
    char buf[256];
    size_t n = serializeJson(doc, buf, sizeof(buf));
    bool ok = mqtt.publish(TOPIC_TELEMETRY.c_str(), (const uint8_t *)buf, n, false);
    lastTelemetryMs = millis();
    Serial.printf("[mqtt] telemetry (%s) %s: %s\n", reason, ok ? "sent" : "FAILED", buf);
    return ok;
}

void ensureWifi() {
    if (WiFi.status() == WL_CONNECTED) {
        if (!wifiWasConnected) {
            wifiWasConnected = true;
            Serial.printf("[wifi] connected, IP %s, RSSI %d dBm\n", WiFi.localIP().toString().c_str(), WiFi.RSSI());
            configTime(TZ_OFFSET_SEC, 0, NTP_SERVER_1, NTP_SERVER_2);
        }
        return;
    }
    if (wifiWasConnected) {
        wifiWasConnected = false;
        Serial.println("[wifi] connection lost");
    }
    uint32_t now = millis();
    if (now - lastWifiAttemptMs >= WIFI_RETRY_MS || lastWifiAttemptMs == 0) {
        lastWifiAttemptMs = now;
        Serial.printf("[wifi] connecting to %s ...\n", WIFI_SSID);
        WiFi.disconnect();
        WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    }
}

void ensureMqtt() {
    if (mqtt.connected() || WiFi.status() != WL_CONNECTED) {
        return;
    }
    uint32_t now = millis();
    if (lastMqttAttemptMs != 0 && now - lastMqttAttemptMs < mqttBackoffMs) {
        return;
    }
    lastMqttAttemptMs = now;
    Serial.printf("[mqtt] connecting to %s:%d as %s ...\n", MQTT_HOST, MQTT_PORT, MQTT_USERNAME);

    // Last Will: the broker publishes this (retained) if we vanish without a clean DISCONNECT.
    bool ok = mqtt.connect(DEVICE_ID, MQTT_USERNAME, MQTT_PASSWORD,
                           TOPIC_STATUS.c_str(), 1, true, WILL_PAYLOAD);
    if (!ok) {
        Serial.printf("[mqtt] failed, state=%d, retry in %lu ms\n", mqtt.state(), (unsigned long)mqttBackoffMs);
        mqttBackoffMs = min(mqttBackoffMs * 2, MQTT_BACKOFF_MAX_MS);
        return;
    }
    mqttBackoffMs = 1000;
    Serial.println("[mqtt] connected");
    publishStatus("ONLINE");
    publishTelemetry("connect");
}

}  // namespace

void setup() {
    Serial.begin(115200);
    delay(200);
    Serial.printf("\n=== Seat node %s (%s) fw %s ===\n", SEAT_CODE, DEVICE_ID, FW_VERSION);
    Serial.printf("telemetry: %s\n", TOPIC_TELEMETRY.c_str());

    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    sensor.begin();

    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.setSleep(false); // keeps MQTT latency low

    mqtt.setServer(MQTT_HOST, MQTT_PORT);
    mqtt.setBufferSize(512);
    mqtt.setKeepAlive(15);        // broker declares us dead (-> LWT) after ~22 s of silence
    mqtt.setSocketTimeout(5);
}

void loop() {
    ensureWifi();
    ensureMqtt();
    mqtt.loop();

    uint32_t now = millis();
    if (now - lastSampleMs >= SAMPLE_INTERVAL_MS) {
        lastSampleMs = now;
        float raw = sensor.readCm();
        bool changed = filter.update(raw, millis());
        digitalWrite(LED_PIN, filter.isPresent() ? HIGH : LOW);
        if (changed) {
            Serial.printf("[seat] %s (avg %.1f cm)\n", filter.isPresent() ? "PRESENT" : "EMPTY", filter.averageCm());
            publishTelemetry("change");
        }
    }

    if (now - lastTelemetryMs >= HEARTBEAT_INTERVAL_MS) {
        publishTelemetry("heartbeat");
    }
}
