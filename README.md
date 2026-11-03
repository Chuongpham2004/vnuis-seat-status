# Smart Self-Study Space Platform

**Nền tảng thông minh quản lý và đặt chỗ không gian tự học dựa trên IoT và AI dự báo lưu lượng**

Graduation capstone: real-time library seat monitoring (ESP32 + HC-SR04 and a multi-seat simulator over MQTT), booking with dual check-in, credit scores, AI occupancy forecasting, and an LLM assistant that can find and book seats.

## Repository layout

| Folder | What | Status |
|---|---|---|
| `infra/` | Docker Compose: PostgreSQL 16, Redis 7 (expiry events), EMQX 5 | ✅ Phase 1 |
| `backend-core/` | Spring Boot 3 / Java 21 — seat state machine, booking, MQTT, WebSocket. Flyway migrations live in `src/main/resources/db/migration` | 🟡 migrations only |
| `iot-edge/` | ESP32 firmware (PlatformIO) | ⏳ Phase 3 |
| `iot-simulator/` | Python virtual seats + control API/CLI | ⏳ Phase 3 |
| `ai-service/` | FastAPI — XGBoost forecast + Claude tool-calling agent | ⏳ Phase 4 |
| `frontend-web/` | React (Vite) + Tailwind — student map, admin dashboard (currently the earlier Firebase prototype) | ⏳ Phase 5 |
| `docs/` | Architecture, MQTT contract | |

See [docs/architecture.md](docs/architecture.md) and [docs/mqtt-contract.md](docs/mqtt-contract.md).

## Quick start (infrastructure)

Requirements: Docker Desktop.

```sh
docker compose -f infra/docker-compose.yml up -d --wait                 # postgres, redis, emqx
docker compose -f infra/docker-compose.yml --profile migrate run --rm flyway migrate
```
On Windows you can use `./scripts/dev.ps1 up` and `./scripts/dev.ps1 migrate` (or `make up migrate` where `make` exists).

| Service | Address | Dev credentials |
|---|---|---|
| PostgreSQL | `localhost:5432`, db `library` | `app` / `app_dev_pw` |
| Redis | `localhost:6379` | — |
| MQTT | `tcp://localhost:1883` | see `infra/emqx/auth-bootstrap.csv` |
| EMQX dashboard | http://localhost:18083 | `admin` / `library_admin` |

Seeded accounts (password `Password@123`): `ADMIN001` (admin), `SV001`–`SV005` (students; `SV005` starts at 65 credit to demo the ban rule).

Seed data: zones A (quiet), B (general), C (group), 20 seats each. Seat `A-01` is bound to the physical ESP32 (`ESP32-A01`); the other 59 seats are simulated.

Reset everything (deletes volumes): `./scripts/dev.ps1 reset`.
