"""Access to hourly_occupancy_stats and zones."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol

import pandas as pd
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

HISTORY_COLUMNS = ["zone_code", "bucket_hour", "avg_occupancy_rate", "is_exam_period"]


@dataclass(frozen=True)
class ZoneRow:
    zone_id: str
    code: str
    name: str
    quiet: bool
    total_seats: int


class StatsRepository(Protocol):
    def zones(self) -> list[ZoneRow]: ...
    def load_history(self, since: datetime | None = None) -> pd.DataFrame: ...
    def upsert(self, df: pd.DataFrame) -> int: ...
    def count(self) -> int: ...


class PostgresStatsRepository:
    def __init__(self, engine: Engine):
        self.engine = engine

    @classmethod
    def from_url(cls, url: str) -> "PostgresStatsRepository":
        return cls(create_engine(url, pool_pre_ping=True, pool_size=5))

    def zones(self) -> list[ZoneRow]:
        with self.engine.connect() as conn:
            rows = conn.execute(text(
                "SELECT id::text, code, name, is_quiet_zone, total_seats FROM zones ORDER BY code")).all()
        return [ZoneRow(*r) for r in rows]

    def load_history(self, since: datetime | None = None) -> pd.DataFrame:
        sql = """
            SELECT z.code AS zone_code, s.bucket_hour, s.avg_occupancy_rate::float AS avg_occupancy_rate,
                   s.is_exam_period
              FROM hourly_occupancy_stats s JOIN zones z ON z.id = s.zone_id
             WHERE (CAST(:since AS timestamptz) IS NULL OR s.bucket_hour >= :since)
             ORDER BY s.bucket_hour, z.code
        """
        with self.engine.connect() as conn:
            df = pd.read_sql(text(sql), conn, params={"since": since})
        if df.empty:
            return pd.DataFrame(columns=HISTORY_COLUMNS)
        df["bucket_hour"] = pd.to_datetime(df["bucket_hour"], utc=True)
        return df[HISTORY_COLUMNS]

    def upsert(self, df: pd.DataFrame) -> int:
        """Insert or overwrite (zone, hour) rows. Expects the columns produced by synthetic.generate()."""
        if df.empty:
            return 0
        zone_ids = {z.code: z.zone_id for z in self.zones()}
        rows = [
            {
                "zone_id": zone_ids[r.zone_code],
                "bucket_hour": r.bucket_hour.to_pydatetime(),
                "rate": round(float(r.avg_occupancy_rate), 4),
                "dow": int(r.day_of_week),
                "weekend": bool(r.is_weekend),
                "exam": bool(r.is_exam_period),
            }
            for r in df.itertuples(index=False)
            if r.zone_code in zone_ids
        ]
        sql = text("""
            INSERT INTO hourly_occupancy_stats
                (zone_id, bucket_hour, avg_occupancy_rate, day_of_week, is_weekend, is_exam_period)
            VALUES (CAST(:zone_id AS uuid), :bucket_hour, :rate, :dow, :weekend, :exam)
            ON CONFLICT (zone_id, bucket_hour) DO UPDATE
               SET avg_occupancy_rate = EXCLUDED.avg_occupancy_rate,
                   day_of_week = EXCLUDED.day_of_week,
                   is_weekend = EXCLUDED.is_weekend,
                   is_exam_period = EXCLUDED.is_exam_period
        """)
        with self.engine.begin() as conn:
            for i in range(0, len(rows), 2000):
                conn.execute(sql, rows[i:i + 2000])
        return len(rows)

    def count(self) -> int:
        with self.engine.connect() as conn:
            return int(conn.execute(text("SELECT count(*) FROM hourly_occupancy_stats")).scalar_one())


class InMemoryStatsRepository:
    """Same interface, for tests and offline experiments."""

    def __init__(self, zones: list[ZoneRow] | None = None):
        self._zones = zones or [
            ZoneRow("00000000-0000-0000-0000-00000000000a", "A", "Khu A", True, 20),
            ZoneRow("00000000-0000-0000-0000-00000000000b", "B", "Khu B", False, 20),
            ZoneRow("00000000-0000-0000-0000-00000000000c", "C", "Khu C", False, 20),
        ]
        self._df = pd.DataFrame(columns=HISTORY_COLUMNS)

    def zones(self) -> list[ZoneRow]:
        return list(self._zones)

    def load_history(self, since: datetime | None = None) -> pd.DataFrame:
        df = self._df
        if since is not None and not df.empty:
            df = df[df["bucket_hour"] >= pd.Timestamp(since)]
        return df.sort_values(["bucket_hour", "zone_code"]).reset_index(drop=True)

    def upsert(self, df: pd.DataFrame) -> int:
        new = df[HISTORY_COLUMNS].copy()
        merged = pd.concat([self._df, new], ignore_index=True) if not self._df.empty else new
        self._df = merged.drop_duplicates(subset=["zone_code", "bucket_hour"], keep="last").reset_index(drop=True)
        return len(new)

    def count(self) -> int:
        return len(self._df)
