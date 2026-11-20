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
| POST | `/reservations` `{"seatCode":"A-05","source":"APP_MAP","faceToken":"..."}` | 201 `ReservationView` (PENDING) | 409 `SEAT_NOT_AVAILABLE` / `SEAT_BUSY` / `USER_HAS_ACTIVE_RESERVATION`, 403 `USER_BANNED` / `INSUFFICIENT_CREDIT` / `FACE_VERIFICATION_REQUIRED` |
| GET | `/reservations/me/active` | 200 `ReservationView`, or 204 | |
| GET | `/reservations/me?limit=20` | history | |
| POST | `/reservations/{id}/check-in` `{"qrCodeToken":"..."}` | 200 (ACTIVE, seat OCCUPIED) | 400 `INVALID_QR_CODE`, 409 `NO_PHYSICAL_PRESENCE`, 410 `RESERVATION_EXPIRED` |
| POST | `/reservations/{id}/check-out` | 200 (COMPLETED, +2 credit if checked in on time) | 409 `INVALID_RESERVATION_STATE` |
| POST | `/reservations/{id}/cancel` | 200 (CANCELLED) | 409 `INVALID_RESERVATION_STATE` |

`ReservationView` = `{id, seatCode, zoneCode, status, bookingSource, reservedAt, expireAt, checkInAt, checkOutAt, secondsUntilExpiry}`.

`faceToken` is required while `app.face.enabled` (env `FACE_VERIFY_ENABLED`, on in docker-compose): it comes from
`POST /face/verify`, is valid 120 s, belongs to one student and is spent by one booking.

## Face verification (before every booking)
Detection YOLOv8n-face → 106 landmarks → ArcFace (MobileFaceNet) embedding in **face-service**; backend-core keeps the
template, compares (cosine ≥ `FACE_MATCH_THRESHOLD`, default 0.45) and issues the booking token. No photo is stored.

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | `/face/me` | `{enrolled, enrolledAt, model, requiredForBooking}` | |
| POST | `/face/challenge` | `{challengeId, steps:["CENTER","LEFT","RIGHT"], expiresAt}` (90 s, single use; LEFT/RIGHT order is random) | 429 `FACE_VERIFY_LOCKED` |
| POST | `/face/enroll` `{challengeId, consent:true, frames:[{step, image}]}` | 200 face status | 400 `CONSENT_REQUIRED` / `INVALID_IMAGE`, 409 `FACE_ALREADY_ENROLLED`, 410 `FACE_CHALLENGE_EXPIRED`, 422 `NO_FACE` / `MULTIPLE_FACES` / `FACE_LIVENESS_FAILED`, 503 `FACE_SERVICE_UNAVAILABLE` |
| POST | `/face/verify` `{challengeId, frames:[{step, image}]}` | `{faceToken, score, expiresAt}` | 409 `FACE_NOT_ENROLLED`, 410, 422 `FACE_NOT_MATCHED` / `NO_FACE` / `MULTIPLE_FACES` / `FACE_LIVENESS_FAILED`, 429 `FACE_VERIFY_LOCKED` (5 liveness/match failures in 10 min), 503 |
| DELETE | `/face/me` | 204 (withdraw consent: template deleted) | |

`frames`: up to 12 base64 JPEGs (≈480 px wide), each tagged with the challenge step it was captured for, taken from the
**un-mirrored** front camera ("LEFT" = the student's own left).

## AI tool endpoints (called by ai-service with the student's forwarded JWT)
| Tool | Endpoint |
|---|---|
| `find_optimal_seats(zone_preference, is_quiet_zone, has_power_outlet, seat_type)` | GET `/ai-tools/optimal-seats?zonePreference=&isQuietZone=&hasPowerOutlet=&seatType=&limit=` |
| `get_occupancy_forecast(zone_code, target_hour)` | GET `/ai-tools/occupancy?zoneCode=` (live + last 24 h), combined with ai-service's own forecast. |
| `reserve_seat(seat_code)` | POST `/ai-tools/reservations` `{"seatCode":"B-07"}` → books for the **token's** user with source `AI_CHATBOT`. With face verification on it returns 403 `FACE_VERIFICATION_REQUIRED`; the web app then shows a button that runs the face check and calls `POST /reservations` with `source: AI_CHATBOT`. |

## AI (proxied to ai-service, same paths)
| Method | Path | Notes |
|---|---|---|
| GET 🔓 | `/ai/forecast?zoneCode=A&hours=24` | `{zoneCode, model, rmse, cache:{hit,key,ttlSeconds}, points:[{time, hour, predictedRate, predictedPercent, lower, upper, level}]}` |
| GET 🔓 | `/ai/metrics` | `{models:{baseline_moving_average, lightgbm}:{mae,rmse,mape,r2}, perZone, improvementPct, featureImportance, testPeriod}` |
| POST | `/ai/chat` `{"message":"...","sessionId":"..."}` | `{sessionId, reply, language, engine, intent, toolCalls:[{name, arguments, ok, result|error}], suggestedSeats, reservation}` |
| POST 🛡 | `/ai/admin/synthetic` `{"days":180}`, `/ai/admin/train` | seed history / retrain |

503 `AI_SERVICE_UNAVAILABLE` when ai-service is down.

## Admin 🛡
| Method | Path | Notes |
|---|---|---|
| GET | `/admin/devices` | `[{deviceId, seatCode, zoneCode, simulated, online, lastHeartbeat, thresholdCm, mqttTopic}]` |
| GET | `/admin/users?q=&limit=` | search by code / name / email |
| PATCH | `/admin/users/{id}/credit` | `{"delta":10,"reason":"appeal accepted","clearBan":true}` |
| DELETE | `/admin/users/{id}/face` | 204 – reset the enrolled face (student enrols again at the next booking) |
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
