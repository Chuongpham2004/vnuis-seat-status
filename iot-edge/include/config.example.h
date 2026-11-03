// Seat node configuration.
// Copy this file to include/config.h and fill in your Wi-Fi and broker details.
// config.h is git-ignored; if it is missing, the build falls back to this file.
#pragma once

// ---------- Wi-Fi ----------
#define WIFI_SSID          "YOUR_WIFI_SSID"
#define WIFI_PASSWORD      "YOUR_WIFI_PASSWORD"

// ---------- MQTT (EMQX) ----------
// LAN IP of the computer running infra/docker-compose.yml (not "localhost").
#define MQTT_HOST          "192.168.1.100"
#define MQTT_PORT          1883
// Must match infra/emqx/auth-bootstrap.csv (usernames starting with "esp32" may publish seat topics).
#define MQTT_USERNAME      "esp32-a01"
#define MQTT_PASSWORD      "esp32_dev_pw"

// ---------- Identity (must match iot_devices / seats seed data) ----------
#define DEVICE_ID          "ESP32-A01"
#define ZONE_CODE          "A"        // topic: library/zone/A/seat/A-01/...
#define SEAT_CODE          "A-01"
#define FW_VERSION         "1.0.0"

// ---------- Pins ----------
// Default wiring: TRIG -> GPIO 12, ECHO -> GPIO 14 through a 1k/2k voltage divider
// (HC-SR04 echo is 5 V, ESP32 inputs are 3.3 V).
// GPIO 12 is a strapping pin (selects flash voltage at boot). As TRIG it is an output and the
// sensor's TRIG input does not pull it high, so boot is normally fine. If uploads/boot ever fail
// with the sensor attached, unplug TRIG while flashing or use the alternative wiring:
//   TRIG -> GPIO 5, ECHO -> GPIO 18.
#define TRIG_PIN           12
#define ECHO_PIN           14
#define LED_PIN            2          // on-board LED: lit while someone is detected

// ---------- Presence detection ----------
#define THRESHOLD_CM          50.0f   // closer than this = someone is sitting
#define MOVING_AVG_WINDOW     5       // samples in the moving average
#define DEBOUNCE_MS           5000    // state must hold this long before it flips
#define SAMPLE_INTERVAL_MS    200     // one (median-of-3) reading every 200 ms
#define MAX_RANGE_CM          400.0f  // readings above this are clamped
#define NO_ECHO_DISTANCE_CM   350.0f  // pulseIn timeout (nothing within ~4 m) = open space = EMPTY

// ---------- Publishing ----------
#define HEARTBEAT_INTERVAL_MS 30000   // telemetry also sent every 30 s while idle

// ---------- Time (for the "ts" field) ----------
#define NTP_SERVER_1       "pool.ntp.org"
#define NTP_SERVER_2       "time.google.com"
#define TZ_OFFSET_SEC      (7 * 3600) // Asia/Ho_Chi_Minh
