# Hướng dẫn demo end-to-end (chỉ dùng mô phỏng, không cần ESP32)

Toàn bộ 60 ghế do `iot-simulator` đóng vai, kể cả `A-01` (bật `SIMULATE_ESP32=true`).
Backend chạy profile `demo` để rút ngắn thời gian chờ, nên cả buổi demo khoảng 20–25 phút.

| Bộ đếm | Thực tế | Profile `demo` |
|---|---|---|
| Giữ chỗ chờ check-in (no-show) | 15 phút | **60 s** |
| Có người ngồi liên tục → walk-in | 2 phút | **20 s** |
| Rời ghế → chuyển AWAY | 1 phút | **10 s** |
| AWAY → tự giải phóng | 30 phút | **90 s** |

---

## 0. Chuẩn bị (làm trước buổi bảo vệ)

1. Mở Docker Desktop.
2. Tạo `infra/.env` (file này đã được gitignore) với 2 dòng:

   ```env
   DEMO_PROFILE=,demo
   SIMULATE_ESP32=true
   ```

   Nếu muốn trợ lý dùng LLM thật thì thêm `ANTHROPIC_API_KEY=...` (hoặc OpenAI/Gemini/Ollama, xem `infra/.env.example`).
   Không có key thì trợ lý dùng engine tất định có sẵn, vẫn tìm ghế, dự báo và đặt chỗ được.
3. Dựng toàn bộ stack từ đầu (xoá dữ liệu cũ để điểm uy tín và lịch sử sạch):

   ```powershell
   ./scripts/dev.ps1 reset      # xoá volume, dựng lại postgres/redis/emqx, chạy Flyway
   ./scripts/dev.ps1 app        # build + chạy backend, ai-service, simulator, frontend
   docker compose -f infra/docker-compose.yml ps
   ```

   Lần đầu ai-service tự sinh 180 ngày dữ liệu tổng hợp và huấn luyện model, mất khoảng 1–2 phút.
   Lần build đầu tiên face-service tải khoảng 140 MB model (YOLOv8-face, ArcFace) và đóng gói sẵn vào image, nên các lần sau chạy được không cần mạng.
   Xem tiến độ: `docker logs -f lib-ai`.
4. Kiểm tra:
   - http://localhost:8080/actuator/health trả về `UP`
   - http://127.0.0.1:8090/docs (Swagger của simulator) mở được
   - App: **http://127.0.0.1:5173**. Dùng `127.0.0.1`, đừng dùng `localhost`, vì Vite của project khác có thể đang chiếm `localhost:5173`.
5. Chuẩn bị màn hình:
   - **Cửa sổ 1 (sinh viên):** đăng nhập `SV001`
   - **Cửa sổ 2 (ẩn danh, admin):** đăng nhập `ADMIN001`, mở `/admin/devices`
   - **Terminal (tuỳ chọn):** `./scripts/dev.ps1 mqtt-sub` để hội đồng thấy luồng MQTT thô

Tài khoản (mật khẩu `Password@123`, trang đăng nhập có nút bấm nhanh): `ADMIN001`, `SV001`–`SV004` (100 điểm), `SV005` (65 điểm).

> Mẹo: ngay đầu buổi, trên `/admin/devices` bấm **Chế độ: Thủ công** để các ghế không tự thay đổi trong lúc bạn đang trình bày.
> Ghế nào bạn đã bấm điều khiển sẽ bị "ghim", chế độ tự động không đụng tới ghế đó cho tới khi bấm **Thả về tự động**.

Điều khiển simulator có 2 cách, kết quả như nhau:
- Giao diện: `/admin/devices` → chọn ghế → **Ngồi / Rời đi / Mất kết nối / Kết nối lại / Thả về tự động**
- Dòng lệnh: `docker exec lib-simulator python -m simulator sit A-05`
  (dùng cách này cho `A-01`, vì dropdown trên giao diện chỉ liệt kê các thiết bị `SIM-*`)

---

## 1. Kịch bản demo

### Bước 1 — Sơ đồ ghế realtime (2 phút)
- Cửa sổ 1, trang `/`: sơ đồ 3 khu A (yên tĩnh), B (chung), C (nhóm), mỗi khu 20 ghế. Mỗi trạng thái có màu, icon và nhãn riêng. Dùng bộ lọc theo khu và trạng thái.
- Bấm một ghế để mở panel chi tiết: trạng thái, khoảng cách cảm biến, thiết bị online.
- Cửa sổ 2 bấm **Ngồi** cho `B-03`, sau ~20 s ghế chuyển **Đang dùng (walk-in)** ở cả 2 cửa sổ mà không cần reload (STOMP).
- **Điểm nhấn:** MQTT → backend → Redis state machine → WebSocket, độ trễ dưới 1 giây. Terminal `mqtt-sub` hiện telemetry `distanceCm`/`objectDetected`.

### Bước 2a — Xác thực khuôn mặt bằng YOLO + ArcFace (3 phút)
Cửa sổ 1 (laptop có webcam, mở bằng `http://127.0.0.1:5173` để trình duyệt cho dùng camera):
1. Bấm ghế `A-05` → **Đặt ghế**. Tài khoản chưa đăng ký nên được chuyển sang trang **Khuôn mặt của tôi**. Đọc phần giải thích (không lưu ảnh, chỉ lưu vector 512 chiều), tick ô **đồng ý**, rồi bấm **Mở camera để đăng ký** → **Bắt đầu**. Làm theo hướng dẫn: nhìn thẳng, quay trái, quay phải (thứ tự trái/phải ngẫu nhiên). Xong sẽ quay về sơ đồ, panel ghế `A-05` tự mở lại.
2. Bấm **Đặt ghế A-05** lần nữa: hiện hộp thoại **Xác thực khuôn mặt**. Làm lại thử thách, khớp mặt thì ghế được giữ ngay.
3. Ca âm (tuỳ chọn, rất thuyết phục):
   - Nhờ một bạn khác ngồi trước camera → lỗi *"Khuôn mặt không khớp…"* (`FACE_NOT_MATCHED`).
   - Giơ ảnh chân dung (trên điện thoại) và xoay ảnh → lỗi *"Chưa nhận ra động tác quay đầu…"* (`FACE_LIVENESS_FAILED`): ảnh phẳng không có thị sai nên mũi luôn nằm giữa hai mắt.
   - Hai người cùng nhìn vào camera → `MULTIPLE_FACES`.
4. Cửa sổ 2 `/admin/users`: cột **Khuôn mặt** hiện *Đã đăng ký*, có nút **Đặt lại** (thủ thư reset khi sinh viên đổi diện mạo).
- **Điểm nhấn:** YOLO chỉ *phát hiện* khuôn mặt (ở đâu), ArcFace mới *nhận dạng* (là ai). Ảnh không bao giờ được lưu. Mỗi lần xác thực thành công sinh một token dùng 1 lần, hạn 120 s, gắn với đúng sinh viên đó. Đặt chỗ hộ hoặc dùng lại token đều bị chặn (`FACE_VERIFICATION_REQUIRED`).

> Mỗi tài khoản dùng trong buổi demo (`SV001`, `SV003`, `SV005`) đều phải đăng ký khuôn mặt một lần. Người trình bày có thể đăng ký chính mặt mình cho cả ba tài khoản.
> Nếu không có webcam: đặt `FACE_VERIFY_ENABLED=false` trong `infra/.env` rồi `./scripts/dev.ps1 app`. Việc đặt chỗ sẽ quay về như trước, không cần xác thực khuôn mặt.

### Bước 2 — Đặt chỗ + check-in kép QR + cảm biến (4 phút)
1. Cửa sổ 2 mở `/admin/qr` và ghi lại mã in dưới QR của ghế `A-05`.
2. Cửa sổ 1 bấm ghế `A-05` → **Đặt chỗ** (qua xác thực khuôn mặt như Bước 2a). Ghế chuyển **Đã giữ**, trang `/me` đếm ngược 60 s.
3. Thử check-in khi chưa ngồi: nhập mã QR → bấm Check-in → lỗi *"Cảm biến chưa phát hiện bạn ngồi vào ghế…"* (`NO_PHYSICAL_PRESENCE`). Đây là lý do cần check-in kép: không thể check-in từ xa.
4. Thử mã sai → lỗi `INVALID_QR_CODE`.
5. Cửa sổ 2 bấm **Ngồi** cho `A-05`, sau đó nhập mã đúng → Check-in. Đặt chỗ chuyển **ACTIVE**, ghế chuyển **Đang dùng**.

### Bước 3 — Tạm vắng và quay lại (2 phút)
- Cửa sổ 2 bấm **Rời đi** cho `A-05`, sau ~10 s ghế chuyển **Tạm vắng (AWAY)** và bộ đếm 90 s bắt đầu.
- Bấm **Ngồi** lại, ghế về **Đang dùng** ngay.
- (Tuỳ chọn) bấm **Rời đi** rồi chờ 90 s: ghế tự giải phóng về **Trống**. Đây là cơ chế chống "chiếm chỗ bằng balo".

### Bước 4 — Check-out và cộng điểm (1 phút)
- Ghế đang **Đang dùng**: cửa sổ 1 vào `/me` → **Trả chỗ**. Ghế về **Trống**, điểm uy tín **+2** (tối đa 100, nên tài khoản đang 100 điểm sẽ không tăng thêm).
- Lịch sử đặt chỗ hiện thời lượng sử dụng.

### Bước 5 — Mất kết nối cảm biến (Last Will) (2 phút)
- Dùng một ghế đang **Đang dùng** (ví dụ `B-03` ở Bước 1), bấm **Mất kết nối**. Simulator cắt TCP mà không gửi DISCONNECT, broker phát Last Will `OFFLINE`.
- Kết quả: thiết bị ở `/admin/devices` chuyển OFFLINE, ghế trên sơ đồ có sọc, chuyển ngay sang **Tạm vắng**. Sau 90 s ghế tự giải phóng nếu thiết bị không quay lại.
- Bấm **Kết nối lại**, thiết bị ONLINE trở lại.

### Bước 6 — No-show, trừ điểm và khoá tài khoản (3 phút)
1. Đăng xuất cửa sổ 1, đăng nhập `SV005` (65 điểm).
2. Đặt ghế `C-10` rồi **không** check-in. Chờ 60 s.
3. Kết quả: đặt chỗ hết hạn, ghế về **Trống**, điểm 65 → **55**. Vì thấp hơn mức tối thiểu 60, tài khoản **bị khoá 3 ngày**.
4. Thử đặt ghế khác, bị từ chối (`USER_BANNED`).
5. Cửa sổ 2 vào `/admin/users`, tìm `SV005` → **gỡ khoá** / chỉnh điểm. Sinh viên đặt chỗ lại được.

### Bước 7 — Chống đặt trùng (1 phút, tuỳ chọn)
- Mở thêm một cửa sổ ẩn danh với `SV002`. Hai sinh viên cùng bấm đặt một ghế trống: chỉ một người thành công, người kia nhận `SEAT_NOT_AVAILABLE`. Khoá phân tán Redisson tuần tự hoá mọi thao tác trên cùng một ghế.
- Một sinh viên đang có đặt chỗ mà đặt thêm ghế khác thì bị chặn (`USER_HAS_ACTIVE_RESERVATION`).

### Bước 8 — Giờ cao điểm và dashboard admin (2 phút)
- `/admin/devices` → **Kịch bản: Giờ cao điểm**. Khoảng 70% ghế trống có người ngồi, sơ đồ đầy dần theo thời gian thực.
- `/admin`: các ô KPI, phân bổ theo khu, heatmap ngày × giờ, luồng sự kiện trực tiếp.
- **Mọi người rời đi** → sơ đồ trống dần (qua AWAY rồi giải phóng). **Đặt lại** → về trạng thái sạch.
- (Tuỳ chọn) bấm **Chế độ: Tự động** để thư viện "sống" như ngày thường: sóng sinh viên đến/đi, nghỉ giải lao.

### Bước 9 — Dự báo AI (2 phút)
- `/forecast`: dự báo tỉ lệ lấp đầy theo giờ cho từng khu (LightGBM), dải bất định và giờ vắng nhất.
- Cửa sổ 2 `/admin/ai`: so sánh MAE/RMSE/MAPE/R² của LightGBM với baseline trung bình trượt, độ quan trọng đặc trưng. Có thể bấm sinh dữ liệu và huấn luyện lại ngay trên giao diện.

### Bước 10 — Trợ lý AI đặt chỗ bằng hội thoại (3 phút)
Cửa sổ 1 (sinh viên chưa có đặt chỗ, ví dụ `SV003`) vào `/assistant`:
- *"Tìm cho mình chỗ yên tĩnh còn trống"* → gọi tool `find_optimal_seats`
- *"Khu B lúc 15 giờ có đông không?"* → gọi tool `get_occupancy_forecast`
- *"Đặt ghế A-07 cho mình"* → gọi tool `reserve_seat` bằng JWT của sinh viên. Backend trả `FACE_VERIFICATION_REQUIRED`: chatbot cũng không được đặt hộ khi chưa xác thực khuôn mặt. Trợ lý hiện nút **Xác thực khuôn mặt và đặt A-07**. Bấm nút, làm thử thách, ghế `A-07` chuyển **Đã giữ** trên sơ đồ ở cửa sổ 2 và `/me` hiện đếm ngược.
- **Điểm nhấn:** trợ lý không có quyền riêng, mọi thao tác đi qua cùng API và luật điểm uy tín như khi sinh viên tự bấm.

---

## 2. Sự cố hay gặp

| Triệu chứng | Cách xử lý |
|---|---|
| Mở `localhost:5173` ra app khác | Dùng `http://127.0.0.1:5173` |
| Timer vẫn 15 phút | Thiếu `DEMO_PROFILE=,demo` trong `infra/.env`. Sửa rồi chạy lại `./scripts/dev.ps1 app` |
| `A-01` hiện OFFLINE | Thiếu `SIMULATE_ESP32=true`. Sửa rồi chạy lại `./scripts/dev.ps1 app` |
| Bảng simulator báo "không kết nối được (cổng 8090)" | `docker logs lib-simulator`, rồi `docker restart lib-simulator` |
| Trang dự báo hoặc trợ lý lỗi ngay sau khi khởi động | ai-service còn đang bootstrap. Đợi `docker logs lib-ai` báo train xong |
| Ghế đổi trạng thái lung tung khi đang trình bày | Bấm **Chế độ: Thủ công** |
| Điểm / lịch sử bẩn từ lần tập trước | `./scripts/dev.ps1 reset` rồi `./scripts/dev.ps1 app` |
| Camera không mở / bị từ chối | Mở bằng `http://127.0.0.1:5173` (hoặc localhost), cho phép quyền camera ở thanh địa chỉ. Đóng ứng dụng khác đang dùng webcam (Zoom, Teams) |
| Xác thực báo `FACE_SERVICE_UNAVAILABLE` | `docker logs lib-face`. Lần build đầu cần mạng để tải model |
| Hay bị báo không nhận ra động tác quay đầu | Quay đầu rõ hơn (khoảng 30°), đủ sáng. Có thể nới ngưỡng `FACE_YAW_TURN_MIN=0.2` trong `infra/.env` |
| Bị khoá xác thực (`FACE_VERIFY_LOCKED`) | Chờ 10 phút, hoặc `docker exec lib-redis redis-cli --scan --pattern "face:fail:*"` rồi xoá key |
| Không có webcam | `FACE_VERIFY_ENABLED=false` trong `infra/.env`, chạy lại `./scripts/dev.ps1 app` |
| Muốn demo trên điện thoại | Mở `http://<IP-laptop>:5173` (cùng Wi-Fi). Camera chỉ chạy trên HTTPS/localhost, nên trên điện thoại hãy nhập mã in dưới QR |

Tắt sau buổi demo: `docker compose -f infra/docker-compose.yml --profile app down`.
