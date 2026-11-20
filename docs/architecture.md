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
        FS[face-service<br/>FastAPI + ONNX Runtime:<br/>YOLOv8-face + ArcFace]
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
    BE -- frames, X-Internal-Key --> FS
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
| `face:challenge:{id}` | string `userId\|CENTER,LEFT,RIGHT` | 90 s | head-turn challenge, spent by one enrol/verify |
| `face:token:{token}` | string userId | 120 s | proof of a passed face check, spent by one booking |
| `face:fail:{user_id}` | counter | 600 s | liveness/match failures; 5 → `FACE_VERIFY_LOCKED` |

A seat whose sensor goes OFFLINE is treated as empty: OCCUPIED moves to AWAY immediately, and the away timer releases it unless the device comes back and detects the student. The same check runs at startup for devices that died while the backend was down.

Redis expiry events are best-effort (they can fire late and are lost while the backend is down), so a reconciliation job in backend-core re-checks deadlines from PostgreSQL every 30 s.

## Face verification before booking

```mermaid
sequenceDiagram
    participant FE as frontend-web
    participant BE as backend-core
    participant FS as face-service
    FE->>BE: POST /face/challenge
    BE-->>FE: {challengeId, steps: CENTER + random LEFT/RIGHT}
    Note over FE: front camera, ~3 frames per step
    FE->>BE: POST /face/verify {challengeId, frames}
    BE->>FS: POST /v1/analyze {steps, frames}
    Note over FS: YOLOv8n-face box → 2d106det landmarks → 5 points<br/>yaw per frame → liveness · align 112×112 → ArcFace 512-d
    FS-->>BE: {status, embedding, frames[yaw, pose]}
    Note over BE: cosine(template, probe) ≥ 0.45
    BE-->>FE: {faceToken (120 s, single use)}
    FE->>BE: POST /reservations {seatCode, faceToken}
```

- **Why two models:** YOLO only *detects* faces (where), it cannot tell *who*. ArcFace turns the aligned face into an
  identity embedding; same person ≈ 0.6–0.95 cosine, different people < 0.25 on our checks.
- **Liveness:** the student turns the head in a random order. A printed photo or a phone screen rotated in front of the
  camera keeps the nose on the eye midline (no parallax), so the yaw never reaches the "turned" threshold; all frames
  must also belong to the same face. A pre-recorded video with the same random order could still pass – documented limitation.
- **Privacy:** face-service is stateless and not exposed on the host; only the 512-d template (2 KB `bytea`) is stored,
  with the consent time (Decree 13/2023/ND-CP). `ai_reader` is denied access to `face_profiles`. Students can delete it.
- **Enrolment:** self-service on the first booking, once; replacing it needs a librarian reset. In production, enrolment
  would happen at the library desk against the student card, since a self-enrolled face only proves "same person as at enrolment".
- `FACE_VERIFY_ENABLED=false` turns the whole check off (bookings need no token).
