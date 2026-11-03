-- =====================================================================
-- V1: Core schema for the Smart Self-Study Space platform
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------- users ----------
CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_code   VARCHAR(32)  NOT NULL UNIQUE,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    role           VARCHAR(16)  NOT NULL DEFAULT 'STUDENT'
                   CHECK (role IN ('STUDENT', 'ADMIN')),
    credit_score   INT          NOT NULL DEFAULT 100
                   CHECK (credit_score BETWEEN 0 AND 100),
    banned_until   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- zones ----------
CREATE TABLE zones (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(16)  NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    floor_number   INT          NOT NULL,
    is_quiet_zone  BOOLEAN      NOT NULL DEFAULT FALSE,
    total_seats    INT          NOT NULL DEFAULT 0 CHECK (total_seats >= 0)
);

-- ---------- seats ----------
CREATE TABLE seats (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id           UUID        NOT NULL REFERENCES zones (id),
    seat_code         VARCHAR(16) NOT NULL UNIQUE,
    qr_code_token     VARCHAR(64) NOT NULL UNIQUE,
    has_power_outlet  BOOLEAN     NOT NULL DEFAULT FALSE,
    seat_type         VARCHAR(16) NOT NULL DEFAULT 'SINGLE'
                      CHECK (seat_type IN ('SINGLE', 'GROUP_TABLE')),
    coord_x           INT         NOT NULL,
    coord_y           INT         NOT NULL,
    current_status    VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE'
                      CHECK (current_status IN ('AVAILABLE', 'RESERVED', 'OCCUPIED', 'AWAY')),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_seats_zone ON seats (zone_id);

-- ---------- iot_devices ----------
CREATE TABLE iot_devices (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seat_id              UUID         NOT NULL UNIQUE REFERENCES seats (id),
    device_id            VARCHAR(64)  NOT NULL UNIQUE,
    mac_address          VARCHAR(17),
    mqtt_topic           VARCHAR(255) NOT NULL,
    threshold_cm         INT          NOT NULL DEFAULT 50 CHECK (threshold_cm > 0),
    is_simulated         BOOLEAN      NOT NULL DEFAULT TRUE,
    connectivity_status  VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE'
                         CHECK (connectivity_status IN ('ONLINE', 'OFFLINE')),
    last_heartbeat       TIMESTAMPTZ
);

-- ---------- reservations ----------
CREATE TABLE reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        REFERENCES users (id),          -- NULL for anonymous walk-in
    seat_id         UUID        NOT NULL REFERENCES seats (id),
    status          VARCHAR(24) NOT NULL
                    CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED',
                                      'EXPIRED_NO_SHOW', 'AUTO_RELEASED_AWAY', 'CANCELLED')),
    booking_source  VARCHAR(16) NOT NULL
                    CHECK (booking_source IN ('APP_MAP', 'AI_CHATBOT', 'WALK_IN')),
    reserved_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expire_at       TIMESTAMPTZ,                                 -- NULL for walk-in
    check_in_at     TIMESTAMPTZ,
    check_out_at    TIMESTAMPTZ
);
CREATE INDEX idx_reservations_user ON reservations (user_id);
CREATE INDEX idx_reservations_seat ON reservations (seat_id);
CREATE INDEX idx_reservations_status_expire ON reservations (status, expire_at);

-- Last line of defence against double booking (on top of the Redisson lock):
-- one open reservation per seat, and one open reservation per user.
CREATE UNIQUE INDEX ux_reservations_seat_open
    ON reservations (seat_id) WHERE status IN ('PENDING', 'ACTIVE');
CREATE UNIQUE INDEX ux_reservations_user_open
    ON reservations (user_id) WHERE status IN ('PENDING', 'ACTIVE') AND user_id IS NOT NULL;

-- ---------- credit_logs ----------
CREATE TABLE credit_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id),
    reservation_id  UUID         REFERENCES reservations (id),  -- NULL for admin adjustments
    point_delta     INT          NOT NULL,
    balance_after   INT          NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_credit_logs_user_time ON credit_logs (user_id, created_at DESC);

-- ---------- seat_telemetry_logs ----------
CREATE TABLE seat_telemetry_logs (
    id                    BIGSERIAL PRIMARY KEY,
    seat_id               UUID        NOT NULL REFERENCES seats (id),
    is_object_detected    BOOLEAN     NOT NULL,
    measured_distance_cm  DOUBLE PRECISION,
    recorded_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_telemetry_seat_time ON seat_telemetry_logs (seat_id, recorded_at DESC);

-- ---------- hourly_occupancy_stats ----------
CREATE TABLE hourly_occupancy_stats (
    id                  BIGSERIAL PRIMARY KEY,
    zone_id             UUID         NOT NULL REFERENCES zones (id),
    bucket_hour         TIMESTAMPTZ  NOT NULL,
    avg_occupancy_rate  NUMERIC(5,4) NOT NULL CHECK (avg_occupancy_rate BETWEEN 0 AND 1),
    day_of_week         SMALLINT     NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),  -- ISO: Mon=1..Sun=7
    is_weekend          BOOLEAN      NOT NULL,
    is_exam_period      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ux_hourly_zone_bucket UNIQUE (zone_id, bucket_hour)
);
