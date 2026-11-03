-- =====================================================================
-- V3: Let ai-service (role ai_reader) write the occupancy stats it generates.
-- The role is created by infra/postgres/init; skip quietly where it does not
-- exist (e.g. Testcontainers databases).
-- =====================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_reader') THEN
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO ai_reader;
        GRANT INSERT, UPDATE, DELETE ON hourly_occupancy_stats TO ai_reader;
        GRANT USAGE, SELECT ON SEQUENCE hourly_occupancy_stats_id_seq TO ai_reader;
    END IF;
END
$$;
