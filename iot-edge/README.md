# iot-edge: ESP32 + HC-SR04 seat node (seat A-01)

The firmware for the physical prototype. It measures the distance to the chair, filters it, and publishes to EMQX using the same contract as the simulator (`docs/mqtt-contract.md`):

| Topic | When |
|---|---|
| `library/zone/A/seat/A-01/telemetry` | on every debounced change, plus a heartbeat every 30 s |
| `library/zone/A/seat/A-01/status` | retained `ONLINE` on connect; retained `OFFLINE` Last Will, published by the broker if the board loses power or Wi-Fi |

## Parts

- ESP32 DevKit V1 (30-pin)
- HC-SR04 ultrasonic sensor (5 V)
- 1 kΩ and 2 kΩ resistors (voltage divider for ECHO)
- Breadboard and jumper wires; a USB cable for power and flashing

## Wiring

```
  HC-SR04                    ESP32 DevKit V1
 ┌─────────┐
 │   VCC   ├──────────────── VIN (5 V from USB)
 │   TRIG  ├──────────────── GPIO 12
 │   ECHO  ├───┐
 │   GND   ├─┐ │
 └─────────┘ │ │   1 kΩ
             │ └──/\/\/──┬──────────── GPIO 14   (≈3.3 V when ECHO is high)
             │           │
             │          ┌┴┐
             │          │ │ 2 kΩ
             │          └┬┘
             └───────────┴──────────── GND
```

- **ECHO must go through the divider.** The HC-SR04 drives ECHO at 5 V and ESP32 inputs take at most 3.3 V: 5 V × 2k / (1k + 2k) ≈ 3.3 V.
- **Default pins: TRIG = GPIO 12, ECHO = GPIO 14.** This matches the existing breadboard.
- **GPIO 12 is a strapping pin.** At reset it selects the flash voltage; if it is held HIGH, the board cannot boot or be flashed. As TRIG it is driven by the ESP32 and the sensor only listens, so it normally stays low. If an upload or boot ever fails with the sensor attached, unplug the TRIG wire while flashing, or use the **alternative wiring TRIG = GPIO 5, ECHO = GPIO 18** (set `TRIG_PIN 5` / `ECHO_PIN 18` in `config.h`). That wiring needs no unplugging.
- The on-board LED (GPIO 2) lights while someone is detected.
- Mount the sensor under the desk edge, facing the chair back, about 30-40 cm from a seated student. An empty chair should read well above 50 cm.

## Configure

```sh
cp include/config.example.h include/config.h     # config.h is git-ignored
```

Edit `include/config.h`:

- `WIFI_SSID` and `WIFI_PASSWORD`: a 2.4 GHz network (the ESP32 has no 5 GHz radio).
- `MQTT_HOST`: the **LAN IP of the computer running EMQX** (`ipconfig` → IPv4 address), not `localhost`. Allow inbound TCP 1883 through the Windows firewall.
- `MQTT_USERNAME` / `MQTT_PASSWORD`: `esp32-a01` / `esp32_dev_pw` (from `infra/emqx/auth-bootstrap.csv`).
- `THRESHOLD_CM` (50), `DEBOUNCE_MS` (5000), `MOVING_AVG_WINDOW` (5): tune these if needed.

Without a `config.h` the build falls back to `config.example.h` (with a warning), so CI and first builds still compile.

## Build and flash

Using PlatformIO, either the VS Code extension or `pip install platformio`:

```sh
cd iot-edge
pio run                       # compile
pio run -t upload             # flash (hold BOOT if the upload does not start)
pio device monitor            # serial log at 115200 baud
pio test -e native            # unit tests for the moving average + debounce, on the PC
```

Expected serial log:

```
=== Seat node A-01 (ESP32-A01) fw 1.0.0 ===
[wifi] connected, IP 192.168.1.57, RSSI -58 dBm
[mqtt] connected
[mqtt] telemetry (connect) sent: {"deviceId":"ESP32-A01","seatCode":"A-01","distanceCm":142.3,"objectDetected":false,...}
[seat] PRESENT (avg 34.8 cm)
[mqtt] telemetry (change) sent: {...,"objectDetected":true,...}
```

**Arduino IDE instead:** install the *esp32 by Espressif* board package and the *PubSubClient* and *ArduinoJson* libraries. Create a sketch folder `SeatNode/`, copy in `src/main.cpp` (renamed `SeatNode.ino`), `src/UltrasonicSensor.*`, `lib/PresenceFilter/PresenceFilter.*` and `include/config.h`. Then select the board "DOIT ESP32 DEVKIT V1".

## How detection works

1. Every 200 ms: three pings, and the median is kept (this rejects single-ping spikes). When `pulseIn` times out (no obstacle within ~4 m, e.g. the student stood up and the sensor now faces open space), the reading is **350 cm** (`NO_ECHO_DISTANCE_CM`). That is well above 50 cm, so leaving always reads as empty.
2. A moving average over the last 5 readings.
3. A 5-second time debounce: the average must stay on the other side of 50 cm for 5 s before `objectDetected` flips. Someone walking past, or a student leaning back for a moment, does not change the state.
4. The backend adds its own business timers on top: walk-in after 2 min, AWAY after 1 min of absence, release after 30 min AWAY.

## Check it from the PC

```sh
docker run --rm --network smart-library_default eclipse-mosquitto:2 \
  mosquitto_sub -h emqx -u monitor -P monitor_dev_pw -t 'library/zone/A/seat/A-01/#' -v
```

Unplug the board: within about 22 s (1.5 × the 15 s keep-alive) the broker publishes `{"deviceId":"ESP32-A01","status":"OFFLINE"}`, and the admin dashboard shows the device as OFFLINE.
