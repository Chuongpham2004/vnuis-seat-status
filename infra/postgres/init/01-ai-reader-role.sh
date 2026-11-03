#!/bin/sh
# Runs once, on the first start of an empty Postgres volume.
# Creates a read-only role for ai-service. Write access to
# hourly_occupancy_stats is granted by Flyway migration V3.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    CREATE ROLE ai_reader LOGIN PASSWORD '${AI_DB_PASSWORD}';
    GRANT CONNECT ON DATABASE "${POSTGRES_DB}" TO ai_reader;
    GRANT USAGE ON SCHEMA public TO ai_reader;
    -- Tables are created later by Flyway (as POSTGRES_USER), so grant by default.
    ALTER DEFAULT PRIVILEGES FOR ROLE "${POSTGRES_USER}" IN SCHEMA public
        GRANT SELECT ON TABLES TO ai_reader;
    ALTER DEFAULT PRIVILEGES FOR ROLE "${POSTGRES_USER}" IN SCHEMA public
        GRANT USAGE, SELECT ON SEQUENCES TO ai_reader;
SQL
