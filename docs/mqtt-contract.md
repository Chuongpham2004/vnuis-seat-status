# MQTT Contract

Broker: EMQX 5 — `tcp://<host>:1883` (WebSocket `ws://<host>:8083/mqtt`). QoS 1 everywhere.

## Topics

| Topic | Publisher | Retained | Purpose |
|---|---|---|---|
| `library/zone/{zoneCode}/seat/{seatCode}/telemetry` | ESP32 / simulator | no | Sensor reading: on every debounced presence change + heartbeat every 30 s |
| `library/zone/{zoneCode}/seat/{seatCode}/status` | ESP32 / simulator (and broker via LWT) | **yes** | `ONLINE` on connect, `OFFLINE` as Last Will |
| `library/zone/{zoneCode}/seat/{seatCode}/cmd` | backend-core | no | Reserved for future commands (e.g. change threshold) |

backend-core subscribes to `library/zone/+/seat/+/telemetry` and `library/zone/+/seat/+/status`.

Seat codes contain a hyphen (`A-01`); zone codes are single letters (`A`, `B`, `C`).

## Payloads

### telemetry
```json
{
  "deviceId": "ESP32-A01",
  "seatCode": "A-01",
  "distanceCm": 38.4,
  "objectDetected": true,
  "rssi": -61,
  "simulated": false,
  "fw": "1.0.0",
  "ts": 1762156800000
}
```
- `distanceCm` — moving-average distance (cm); `-1` when the echo timed out.
- `objectDetected` — **debounced** presence: it only flips after the filtered distance has stayed on the other side of `threshold_cm` (default 50 cm) for 5 s.
- `ts` — epoch milliseconds (device clock via NTP; backend uses its own receive time if `ts` is missing or skewed > 60 s).

### status
```json
{ "deviceId": "ESP32-A01", "status": "ONLINE", "ts": 1762156800000 }
```
The client sets this Last Will when connecting (retained, QoS 1):
```json
{ "deviceId": "ESP32-A01", "status": "OFFLINE" }
```

## Credentials / ACL (dev)

Defined in `infra/emqx/auth-bootstrap.csv` and `infra/emqx/acl.conf`.

| Username | Can publish | Can subscribe |
|---|---|---|
| `backend` | `library/#` | `library/#` |
| `simulator` | seat `telemetry`, `status` | seat `cmd` |
| `esp32*` (e.g. `esp32-a01`) | seat `telemetry`, `status` | seat `cmd` |
| `monitor` | — | `library/#` |

Anonymous clients are rejected.

## Quick check
```sh
docker run --rm --network smart-library_default eclipse-mosquitto:2 \
  mosquitto_sub -h emqx -u monitor -P monitor_dev_pw -t 'library/#' -v
```
