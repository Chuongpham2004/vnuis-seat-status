-- =====================================================================
-- V4: Remember when a seat went AWAY so the 30-minute deadline can be
-- rebuilt after a restart (Redis TTL keys alone are not durable enough).
-- =====================================================================
ALTER TABLE seats ADD COLUMN away_since TIMESTAMPTZ;

CREATE INDEX idx_seats_status_away ON seats (current_status, away_since);
