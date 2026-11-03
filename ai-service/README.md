# ai-service: occupancy forecasting + study-space assistant

FastAPI (Python 3.11+). The web app reaches it through backend-core, which proxies `/api/v1/ai/**` with the same paths.

| Endpoint | Auth | |
|---|---|---|
| `GET /api/v1/ai/forecast?zoneCode=A&hours=24` | public | hourly forecast; omit `zoneCode` for all zones. Cached in Redis `cache:forecast:zone:{zone}:{YYYY-MM-DD}` (TTL 3600 s) |
| `GET /api/v1/ai/metrics` | public | MAE / RMSE / MAPE / R² for the baseline vs. LightGBM: overall, per zone, plus feature importance |
| `POST /api/v1/ai/chat` `{"message", "sessionId"?}` | student JWT | assistant reply + `toolCalls` trace + `suggestedSeats` / `reservation` |
| `POST /api/v1/ai/admin/synthetic` `{"days":180}` | ADMIN | seed synthetic history into `hourly_occupancy_stats` |
| `POST /api/v1/ai/admin/train` | ADMIN | retrain and clear the forecast cache |
| `GET /health` | public | includes the active chat engine and whether a model is trained |

## Module A: forecasting

- **Synthetic history** (`app/forecast/synthetic.py`): hourly rows for zones A, B and C.
  - Morning plateau 08:30–11:30 and afternoon plateau 13:30–17:00, with a lunch dip.
  - Zone A fills first; zone C peaks in the afternoon.
  - Weekends run at about 60% (Saturday) and 45% (Sunday) of weekday traffic, and the summer break is quieter.
  - Exam periods (`EXAM_PERIODS`, `is_exam_period = true`) hold 85–98% occupancy through the day.
- **Features** (`features.py`): sin/cos of hour and day-of-week, weekend and exam flags, zone one-hot, lags of 24/48/168 h, the 7-day same-hour mean, and rolling mean/max of the window ending 24 h ago. Every lag is at least 24 h old, so the model forecasts 1–24 h ahead directly. Longer horizons feed predictions back in.
- **Models** (`training.py`):
  - Baseline: seasonal moving average (same hour, previous 7 days).
  - LightGBM gradient boosting.
  - The last 28 days are held out for testing. MAPE is computed over open hours only (actual ≥ 5%), since it is undefined when the library is empty.

Typical result on 180 days:

| Model | MAE | RMSE | MAPE % | R² |
|---|---|---|---|---|
| Seasonal moving average | 0.110 | 0.208 | 56.3 | 0.706 |
| LightGBM | 0.027 | 0.060 | 16.6 | 0.976 |

LightGBM is about 76% lower on MAE and 71% lower on RMSE.

## Module B: assistant

Three tools, defined once in `app/agent/tools.py`:

- `find_optimal_seats(zone_code, is_quiet_zone, has_power_outlet, seat_type)` → `GET /api/v1/ai-tools/optimal-seats`
- `get_occupancy_forecast(zone_code, target_hour, day)` → the local forecast plus live occupancy from `GET /api/v1/ai-tools/occupancy`
- `reserve_seat(seat_code)` → `POST /api/v1/ai-tools/reservations` (booking source `AI_CHATBOT`)

All backend calls carry the **student's own JWT**, so a booking can only ever be made for the signed-in student.

**Engines** (`LLM_PROVIDER=auto` picks the first one configured):

| Engine | Configure with |
|---|---|
| Claude (tool use) | `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` (default `claude-sonnet-5-5`) |
| OpenAI | `OPENAI_API_KEY`, `OPENAI_MODEL`, optional `OPENAI_BASE_URL` |
| Gemini (OpenAI-compatible endpoint) | `GEMINI_API_KEY`, `GEMINI_MODEL` |
| Ollama (local, `/v1`) | `OLLAMA_BASE_URL` (e.g. `http://localhost:11434/v1`), `OLLAMA_MODEL` |
| **Deterministic** (default, offline) | nothing; or force it with `LLM_PROVIDER=deterministic` |

The deterministic engine parses Vietnamese (with or without accents) and English: intents (find, find + book, book a named seat, book one of the earlier suggestions, forecast, help), zone, quiet, power outlet, seat type, and time ("15h", "3 giờ chiều", "3pm", "sáng mai").

If an LLM call fails, the request is answered by the deterministic engine. A booking the LLM already made is confirmed rather than booked again.

## Run

```sh
cd ai-service
python -m venv .venv && .venv/Scripts/activate          # Linux/macOS: source .venv/bin/activate
pip install -e ".[dev]"
python -m app.forecast.synthetic --days 180 --write-db   # seed (or POST /api/v1/ai/admin/synthetic)
python -m app.forecast.training                          # train + print the metrics table
uvicorn app.main:app --port 8000
pytest                                                   # no database, Redis or API key needed
```

Defaults match the local stack: Postgres `localhost:5433` as `ai_reader`, Redis `localhost:6379`, backend-core `localhost:8080`, and the JWT secret shared with backend-core. In Docker (`--profile app`), `AUTO_BOOTSTRAP=true` seeds 180 days and trains on first start.
