# frontend-web

React 19 + Vite + Tailwind 4 web app for students and library staff. It talks only to
backend-core: REST under `/api/v1` and STOMP over WebSocket at `/ws`.

## Run

```sh
npm install
npm run dev        # http://localhost:5173 (also on the LAN: http://<laptop-ip>:5173)
npm test           # Vitest: helpers, API client, components, map page
npm run lint
npm run build
```

The dev server proxies `/api` and `/ws` to backend-core (`localhost:8080`) and `/sim` to the
iot-simulator control API (`localhost:8090`). Override with `DEV_BACKEND_URL` / `DEV_SIMULATOR_URL`
(see `.env.example`). Start the stack first: infra + `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`
in `backend-core`, ai-service and iot-simulator (see the root README).

In Docker (`docker compose -f infra/docker-compose.yml --profile app up -d --build`) the image serves
the built app with nginx on http://localhost:5173 and forwards the same three paths inside the compose network.

## Pages

| Path | Who | What |
|---|---|---|
| `/` | everyone | Live seat map (zones A/B/C on their coordinate grid), filters, seat details, booking, seat suggestions |
| `/me` | student | Open reservation (no-show countdown, QR + sensor check-in, check-out, cancel), credit score, history |
| `/forecast` | everyone | Hourly occupancy forecast per zone (LightGBM) with uncertainty band and quietest hour |
| `/assistant` | student | Chat assistant: find seats, ask forecasts, book by message |
| `/admin` | admin | KPI tiles, zone breakdown, day × hour heatmap, live event feed |
| `/admin/devices` | admin | Sensor list (live ONLINE/OFFLINE), force release, iot-simulator demo controls |
| `/admin/users` | admin | Search students, adjust credit, lift bans |
| `/admin/qr` | admin | Printable QR stickers (the seat's check-in token) |
| `/admin/ai` | admin | Model metrics (MAE/RMSE/MAPE/R² vs baseline), feature importance, synthetic data + retrain |

Demo accounts (password `Password@123`): `SV001`…`SV005`, `ADMIN001`. The login page shows one-click
buttons for them in dev and in the Docker image (`VITE_SHOW_DEMO_ACCOUNTS=true`).

## How it works

- `src/lib/api.js` — fetch wrapper; RFC 7807 errors become `ApiError` with the backend's stable `code`,
  shown in Vietnamese by `src/lib/errors.js`. A 401 on an authenticated call signs the user out.
- `src/realtime/RealtimeContext.jsx` — one `@stomp/stompjs` client for the app. It sends the JWT in the
  CONNECT frame, reconnects when the token changes and re-subscribes after every reconnect.
  `useLiveSeats` reloads seats from REST after a reconnect, because events are lost while the socket is down.
- Seat state colours come from the status palette and always ship with an icon and a label; a seat
  whose sensor is offline is striped. Charts are plain SVG following the dataviz rules (2px lines,
  crosshair tooltip, legend + direct labels, table view, one-hue heatmap ramp).

## Phones and QR check-in

Open `http://<laptop-ip>:5173` on the phone (same Wi-Fi). Both proxies keep the original `Host`
header, so backend-core treats these calls as same-origin and no CORS change is needed.
Browsers only allow the camera on HTTPS or `localhost`; over plain LAN HTTP the student types the
code printed under the QR instead (the camera button appears where `BarcodeDetector` is available).
