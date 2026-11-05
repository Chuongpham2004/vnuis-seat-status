# Architecture

```mermaid
flowchart LR
    subgraph Edge["Edge / IoT"]
        ESP[ESP32 + HC-SR04<br/>debounce + moving avg]
        SIM[iot-simulator<br/>50-100 virtual seats]
    end

    subgraph Infra
        MQ[(EMQX<br/>MQTT broker)]
        RD[(Redis 7<br/>seat state, timers, locks)]
        PG[(PostgreSQL 16)]
    end

    subgraph Backend
        BE[backend-core<br/>Spring Boot 3 / Java 25]
        AI[ai-service<br/>FastAPI: XGBoost + Claude agent]
    end

    FE[frontend-web<br/>React + STOMP]
    LLM[[Claude API]]

    ESP -- telemetry / LWT --> MQ
    SIM -- telemetry / LWT --> MQ
    MQ -- subscribe --> BE
    BE <-- hash, TTL timers, Redisson lock --> RD
    RD -- keyevent expired --> BE
    BE <-- JPA / Flyway --> PG
    BE -- REST proxy: forecast, chat --> AI
    AI -- tool calls with user JWT --> BE
    AI <-- read stats --> PG
    AI <--> LLM
    FE -- REST /api --> BE
    BE -- STOMP /ws --> FE
```

## Seat state machine

```mermaid
stateDiagram-v2
    [*] --> AVAILABLE
    AVAILABLE --> RESERVED: book (app / AI) - 15 min timer
    AVAILABLE --> OCCUPIED: walk-in, presence held 2 min
    RESERVED --> OCCUPIED: dual check-in (QR + sensor)
    RESERVED --> AVAILABLE: no-show timer (-10 credit) / cancel
    OCCUPIED --> AWAY: no presence for 1 min - 30 min timer
    OCCUPIED --> AWAY: sensor OFFLINE (Last Will) - immediately
    AWAY --> OCCUPIED: presence returns
    AWAY --> AVAILABLE: away timer expires (auto release)
    OCCUPIED --> AVAILABLE: check-out (+2 credit if on time)
    AWAY --> AVAILABLE: check-out
```

## Redis keys

| Key | Type | TTL | Meaning |
|---|---|---|---|
| `lock:seat:booking:{seat_code}` | Redisson lock | 5 s lease | serialises all transitions of one seat |
| `seat:state:{seat_code}` | hash | — | `seatId, zoneCode, status, physicalOccupied, lastDistanceCm, currentUserId, currentReservationId, stateUpdatedAt` |
| `timer:reservation:expire:{reservation_id}` | string | 900 s | no-show deadline |
| `timer:seat:away:{seat_code}` | string | 1800 s | away deadline |
| `timer:seat:walkin:{seat_code}` | string | 120 s | presence must hold this long to become a walk-in |
| `timer:seat:awaypending:{seat_code}` | string | 60 s | absence must hold this long to become AWAY |
| `cache:forecast:zone:{zone_code}:{YYYY-MM-DD}` | JSON string | 3600 s | forecast cache |

A seat whose sensor goes OFFLINE is treated as empty: OCCUPIED moves to AWAY immediately, and the away timer releases it unless the device comes back and detects the student. The same check runs at startup for devices that died while the backend was down.

Redis expiry events are best-effort (they can fire late and are lost while the backend is down), so a reconciliation job in backend-core re-checks deadlines from PostgreSQL every 30 s.
