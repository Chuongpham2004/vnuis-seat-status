# iot-simulator: 59 virtual seat nodes

Each virtual seat is its own MQTT client and publishes exactly what the ESP32 firmware does (`docs/mqtt-contract.md`):

- a retained `ONLINE` status on connect,
- a retained `OFFLINE` **Last Will**, published by the broker if the client drops,
- telemetry on every change plus a heartbeat every 30 s.

It manages `A-02..A-20`, `B-01..B-20` and `C-01..C-20`. `A-01` is the physical ESP32 and is skipped. The seat list comes from `GET /api/v1/seats` (seats whose device is simulated), or from the built-in layout when the backend is not running.

## Run

```sh
cd iot-simulator
python -m venv .venv && .venv/Scripts/activate      # Linux/macOS: source .venv/bin/activate
pip install -e ".[dev]"

python -m simulator run                      # ambient mode, real time, control API on :8090
python -m simulator run -i --speed 60        # + interactive console; 1 simulated hour per minute
python -m simulator run --mode manual        # nobody moves unless you say so
```

**No ESP32 at hand?** Set `SIMULATE_ESP32=true` and the simulator also plays `A-01` (device `ESP32-A01`), so all 60 seats are live.

Settings come from env vars (or `.env`): `MQTT_HOST`, `MQTT_PORT`, `MQTT_USER`, `MQTT_PASSWORD`, `BACKEND_URL`, `SIM_SEATS`, `SIMULATE_ESP32`, `MODE`, `SPEED`, `START_HOUR`, `CONTROL_PORT`. The defaults match `infra/docker-compose.yml`.

## Modes

**Ambient (default):** students arrive in waves that follow a daily curve: a morning peak, a lunch dip, an afternoon peak and closing at 22:00. Weekends are quieter. Zone A (quiet) fills first, and the group tables in zone C peak in the afternoon. Students take short breaks (the backend shows the seat as AWAY), and about 8% of breaks are long, so the student never comes back and the backend auto-releases the seat after 30 min.

The arrival rate is set so that the long-run share of seats in use matches the target curve (`behavior.py`, verified in `tests/test_behavior.py`).

**Manual:** ambient behaviour is paused for every seat.

Any seat you command by hand is **pinned**: ambient behaviour leaves it alone until you `release` it. The rest of the library keeps moving around it.

## Demo controls (thesis defense)

In the `run -i` console, or as one-shot commands from another terminal (`python -m simulator <cmd>`):

| Command | What happens | Backend reaction (demo profile) |
|---|---|---|
| `sit A-02` | publishes ~20-45 cm, `objectDetected: true` | AVAILABLE → OCCUPIED (walk-in) after 20 s; AWAY → OCCUPIED at once |
| `stand A-02` | publishes ~90-220 cm, `objectDetected: false` | OCCUPIED → AWAY after 10 s; auto-release after 90 s |
| `disconnect A-02` | drops TCP **without** DISCONNECT | broker publishes the Last Will → device OFFLINE |
| `reconnect A-02` | new connection | retained ONLINE → device ONLINE |
| `release A-02` | back to ambient behaviour | |
| `status [A-02]` | summary / one seat | |
| `mode ambient\|manual` | | |
| `scenario rush_hour\|mass_leave\|reset` | ~70% of free seats sit down / everyone leaves / clean slate | |

HTTP equivalents (Swagger UI at http://127.0.0.1:8090/docs): `POST /seats/{code}/sit|stand|disconnect|reconnect|release`, `POST /mode {"mode":"manual"}`, `POST /scenario/{name}`, `GET /status`, `GET /seats`.

Inside Docker (`docker compose --profile app up`), open the console with `docker exec -it lib-simulator python -m simulator shell`.

## Tests

```sh
pytest                    # unit tests (fake MQTT client), no broker needed
# Real broker + Last Will check against the local EMQX:
SIM_IT_MQTT_HOST=localhost SIM_IT_MQTT_USER=simulator SIM_IT_MQTT_PASSWORD=simulator_dev_pw \
SIM_IT_SUB_USER=monitor SIM_IT_SUB_PASSWORD=monitor_dev_pw pytest -m integration
```
