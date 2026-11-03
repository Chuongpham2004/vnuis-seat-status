# backend-core API (v1)

Base URL `http://localhost:8080/api/v1`. JSON everywhere. Errors are RFC 7807 `application/problem+json` with a stable `code`:

```json
{ "status": 409, "title": "Conflict", "code": "SEAT_NOT_AVAILABLE", "detail": "Seat A-01 is RESERVED" }
```

Auth: `Authorization: Bearer <accessToken>` from `POST /auth/login`. 🔓 = public, 🛡 = ADMIN only, otherwise any signed-in user.

## Auth
| Method | Path | Body / notes |
|---|---|---|
| POST 🔓 | `/auth/login` | `{"username":"SV001" \| "sv001@vnuis.edu.vn","password":"..."}` → `{accessToken, tokenType, expiresInSeconds, user}`; 401 `INVALID_CREDENTIALS` |
| GET | `/auth/me` | `{id, studentCode, email, fullName, role, creditScore, bannedUntil, canBook}` |

## Seats & zones
| Method | Path | Notes |
|---|---|---|
| GET 🔓 | `/zones` | `[{code, name, floorNumber, quietZone, totalSeats, available, reserved, occupied, away, occupancyRate}]` |
| GET 🔓 | `/seats?zone=&quiet=&power=&type=SINGLE\|GROUP_TABLE&status=` | `[SeatView]` |
| GET 🔓 | `/seats/{seatCode}` | `SeatView` |
| GET 🔓 | `/seats/recommendations?zonePreference=&isQuietZone=&hasPowerOutlet=&seatType=&limit=5` | ranked AVAILABLE seats with `score` and `reasons` |

`SeatView` = `{seatCode, zoneCode, seatType, hasPowerOutlet, quietZone, coordX, coordY, status, physicalOccupied, lastDistanceCm, deviceOnline, simulatedDevice, deviceId, updatedAt}`.

## Reservations
| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/reservations` `{"seatCode":"A-05","source":"APP_MAP"}` | 201 `ReservationView` (PENDING) | 409 `SEAT_NOT_AVAILABLE` / `SEAT_BUSY` / `USER_HAS_ACTIVE_RESERVATION`, 403 `USER_BANNED` / `INSUFFICIENT_CREDIT` |
| GET | `/reservations/me/active` | 200 `ReservationView`, or 204 | |
| GET | `/reservations/me?limit=20` | history | |
| POST | `/reservations/{id}/check-in` `{"qrCodeToken":"..."}` | 200 (ACTIVE, seat OCCUPIED) | 400 `INVALID_QR_CODE`, 409 `NO_PHYSICAL_PRESENCE`, 410 `RESERVATION_EXPIRED` |
| POST | `/reservations/{id}/check-out` | 200 (COMPLETED, +2 credit if checked in on time) | 409 `INVALID_RESERVATION_STATE` |
| POST | `/reservations/{id}/cancel` | 200 (CANCELLED) | 409 `INVALID_RESERVATION_STATE` |

`ReservationView` = `{id, seatCode, zoneCode, status, bookingSource, reservedAt, expireAt, checkInAt, checkOutAt, secondsUntilExpiry}`.

## AI tool endpoints (called by ai-service with the student's forwarded JWT)
| Tool | Endpoint |
|---|---|
| `find_optimal_seats(zone_preference, is_quiet_zone, has_power_outlet, seat_type)` | GET `/ai-tools/optimal-seats?zonePreference=&isQuietZone=&hasPowerOutlet=&seatType=&limit=` |
| `get_occupancy_forecast(zone_code, target_hour)` | GET `/ai-tools/occupancy?zoneCode=` (live + last 24 h). The forecast itself is served by ai-service (Phase 4). |
| `reserve_seat_for_user(user_id, seat_code)` | POST `/ai-tools/reservations` `{"seatCode":"B-07"}` → books for the **token's** user with source `AI_CHATBOT`; the LLM's `user_id` is ignored. |

## Admin 🛡
| Method | Path | Notes |
|---|---|---|
| GET | `/admin/devices` | `[{deviceId, seatCode, zoneCode, simulated, online, lastHeartbeat, thresholdCm, mqttTopic}]` |
| GET | `/admin/users?q=&limit=` | search by code / name / email |
| PATCH | `/admin/users/{id}/credit` | `{"delta":10,"reason":"appeal accepted","clearBan":true}` |
| POST | `/admin/seats/{seatCode}/force-release` | 204 |
| GET | `/admin/seats/qr?zone=` | QR tokens for printing table stickers |
| GET | `/admin/occupancy/hourly?from=&to=` | heatmap rows (ISO-8601 instants; default last 7 days) |

## WebSocket (STOMP)
Endpoint `ws://localhost:8080/ws` (SockJS: `/ws-sockjs`). Send `Authorization: Bearer <jwt>` in the CONNECT headers. Anonymous connections may read public topics.

| Destination | Payload |
|---|---|
| `/topic/seats` | `SeatView` on every change |
| `/topic/zones/{zoneCode}/occupancy` | zone counters (same shape as `/zones` items) |
| `/topic/devices` 🛡 | device view on ONLINE/OFFLINE |
| `/user/queue/notifications` | `{type, message, data, at}`; types: `RESERVATION_CREATED`, `CHECKED_IN`, `CHECKED_OUT`, `RESERVATION_EXPIRED`, `AWAY_STARTED`, `RETURNED`, `AUTO_RELEASED`, `CREDIT_CHANGED`, `SEAT_RELEASED_BY_ADMIN` |

## Timers (`app.seat.*`, env vars in brackets)
| Property | Default | Demo profile |
|---|---|---|
| `reservation-ttl-seconds` (`RESERVATION_TTL_SECONDS`) | 900 | 60 |
| `away-ttl-seconds` (`AWAY_TTL_SECONDS`) | 1800 | 90 |
| `walkin-grace-seconds` (`WALKIN_GRACE_SECONDS`) | 120 | 20 |
| `away-grace-seconds` (`AWAY_GRACE_SECONDS`) | 60 | 10 |
