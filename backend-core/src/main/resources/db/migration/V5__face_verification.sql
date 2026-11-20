-- =====================================================================
-- V5: Face verification before booking (YOLOv8-face + ArcFace, see face-service).
-- Only the 512-d ArcFace template is kept, never a photo. Biometric data is
-- sensitive personal data (Decree 13/2023/ND-CP), so consent time is recorded
-- and ai_reader must not see the templates.
-- =====================================================================
CREATE TABLE face_profiles (
    user_id     UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    embedding   BYTEA       NOT NULL,          -- 512 x float32 little-endian, L2-normalised
    model       VARCHAR(64) NOT NULL,          -- which detector/embedder produced it
    consent_at  TIMESTAMPTZ NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per enrol / verify attempt: FAR/FRR, liveness and latency figures for the report.
CREATE TABLE face_verification_log (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose     VARCHAR(16)  NOT NULL CHECK (purpose IN ('ENROLL', 'VERIFY')),
    result      VARCHAR(32)  NOT NULL,         -- OK, FACE_NOT_MATCHED, FACE_LIVENESS_FAILED, NO_FACE, ...
    score       NUMERIC(6, 4),                 -- cosine similarity to the template (VERIFY)
    latency_ms  INT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_face_log_user_time ON face_verification_log (user_id, created_at);

-- infra/postgres/init grants ai_reader SELECT on every new table by default; take it back here.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_reader') THEN
        REVOKE ALL ON face_profiles FROM ai_reader;
    END IF;
END
$$;
