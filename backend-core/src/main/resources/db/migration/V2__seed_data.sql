-- =====================================================================
-- V2: Seed data - 3 zones, 60 seats, 60 IoT devices, sample users
-- Dev password for every seeded account: Password@123
-- =====================================================================

-- ---------- zones ----------
INSERT INTO zones (code, name, floor_number, is_quiet_zone, total_seats) VALUES
    ('A', 'Khu A - Tự học yên tĩnh', 2, TRUE,  20),
    ('B', 'Khu B - Tự học chung',    2, FALSE, 20),
    ('C', 'Khu C - Học nhóm',        3, FALSE, 20);

-- ---------- seats (20 per zone, 5 columns x 4 rows grid) ----------
-- Zone A: single seats, power outlet on every other column.
-- Zone B: single seats, power outlet on the first two rows.
-- Zone C: C-01..C-08 are group tables, the rest are single seats with outlets.
INSERT INTO seats (zone_id, seat_code, qr_code_token, has_power_outlet, seat_type, coord_x, coord_y)
SELECT z.id,
       z.code || '-' || lpad(n::text, 2, '0'),
       encode(gen_random_bytes(16), 'hex'),
       CASE z.code
           WHEN 'A' THEN ((n - 1) % 5) % 2 = 0
           WHEN 'B' THEN n <= 10
           ELSE n > 8
       END,
       CASE WHEN z.code = 'C' AND n <= 8 THEN 'GROUP_TABLE' ELSE 'SINGLE' END,
       (n - 1) % 5,
       (n - 1) / 5
FROM zones z
CROSS JOIN generate_series(1, 20) AS n;

-- ---------- iot_devices (one per seat; A-01 is the physical ESP32) ----------
INSERT INTO iot_devices (seat_id, device_id, mqtt_topic, threshold_cm, is_simulated)
SELECT s.id,
       CASE WHEN s.seat_code = 'A-01' THEN 'ESP32-A01'
            ELSE 'SIM-' || replace(s.seat_code, '-', '') END,
       'library/zone/' || z.code || '/seat/' || s.seat_code,
       50,
       s.seat_code <> 'A-01'
FROM seats s
JOIN zones z ON z.id = s.zone_id;

-- ---------- users ----------
INSERT INTO users (student_code, email, password_hash, full_name, role, credit_score) VALUES
    ('ADMIN001', 'admin@vnuis.edu.vn', crypt('Password@123', gen_salt('bf', 10)), 'Quản trị viên Thư viện', 'ADMIN',   100),
    ('SV001',    'sv001@vnuis.edu.vn', crypt('Password@123', gen_salt('bf', 10)), 'Nguyễn Văn An',          'STUDENT', 100),
    ('SV002',    'sv002@vnuis.edu.vn', crypt('Password@123', gen_salt('bf', 10)), 'Trần Thị Bình',          'STUDENT', 100),
    ('SV003',    'sv003@vnuis.edu.vn', crypt('Password@123', gen_salt('bf', 10)), 'Lê Hoàng Cường',         'STUDENT', 92),
    ('SV004',    'sv004@vnuis.edu.vn', crypt('Password@123', gen_salt('bf', 10)), 'Phạm Minh Duy',          'STUDENT', 78),
    -- Credit 65: one more no-show (-10) drops below 60 and triggers the 3-day ban (demo).
    ('SV005',    'sv005@vnuis.edu.vn', crypt('Password@123', gen_salt('bf', 10)), 'Hoàng Thu Hà',           'STUDENT', 65);
