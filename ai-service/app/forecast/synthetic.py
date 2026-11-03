"""Realistic synthetic hourly occupancy history for zones A, B, C.

Shape of a regular weekday (local time, library open 07:00-22:00):
  * morning peak 08:30-11:30 and afternoon peak 13:30-17:00, a lunch dip, a smaller evening bump;
  * zone A (quiet) fills earliest and highest in the morning; zone C (group tables) peaks in the afternoon;
  * Saturdays ~60 % and Sundays ~45 % of weekday traffic; a quieter summer break;
  * exam periods (is_exam_period = true): 85-98 % occupancy through the core opening hours;
  * day-to-day variation plus autocorrelated hourly noise.
"""

from __future__ import annotations

import math
from datetime import date, datetime, timedelta
from typing import Callable
from zoneinfo import ZoneInfo

import numpy as np
import pandas as pd

ZONES = ("A", "B", "C")

# Per-zone curve: floor + three smooth plateaus (start/end in local hours).
#   (floor, morning amp, morning start, morning end, afternoon amp, afternoon start, afternoon end, evening amp)
ZONE_PROFILE = {
    "A": (0.22, 0.62, 8.0, 11.5, 0.50, 13.5, 17.0, 0.18),   # quiet zone: fills first, highest morning
    "B": (0.18, 0.52, 8.5, 11.5, 0.56, 13.5, 17.0, 0.18),
    "C": (0.12, 0.26, 9.0, 11.5, 0.74, 13.5, 17.25, 0.22),  # group tables: afternoon peak
}
EVENING_WINDOW = (18.5, 20.5)
RAMP_HOURS = 0.6
WEEKEND_FACTOR = {5: 0.60, 6: 0.45}  # date.weekday(): Sat=5, Sun=6
SUMMER_BREAK = ((7, 1), (8, 20))  # month/day range with lighter traffic
SUMMER_FACTOR = 0.55


def _smoothstep(x: float) -> float:
    x = min(max(x, 0.0), 1.0)
    return x * x * (3 - 2 * x)


def open_factor(h: float) -> float:
    """0 when closed, ramps 07:00-08:00 and 21:00-22:00."""
    return _smoothstep(h - 7.0) * (1 - _smoothstep(h - 21.0))


def _window(h: float, start: float, end: float, ramp: float = RAMP_HOURS) -> float:
    """~1 between start and end, smooth ramps of `ramp` hours centred on each edge."""
    rise = _smoothstep((h - (start - ramp / 2)) / ramp)
    fall = 1 - _smoothstep((h - (end - ramp / 2)) / ramp)
    return rise * fall


def weekday_shape(zone: str, h: float) -> float:
    """Expected weekday occupancy (0..1) at fractional local hour h, before noise."""
    floor, am, ms, me, aa, as_, ae, ea = ZONE_PROFILE[zone]
    value = floor + am * _window(h, ms, me) + aa * _window(h, as_, ae) + ea * _window(h, *EVENING_WINDOW)
    return min(value, 0.97) * open_factor(h)


def _peak(zone: str) -> float:
    return max(weekday_shape(zone, h / 10) for h in range(70, 220))


def _is_summer(d: date) -> bool:
    (m1, d1), (m2, d2) = SUMMER_BREAK
    return (m1, d1) <= (d.month, d.day) <= (m2, d2)


def generate(days: int = 180, end: datetime | None = None, seed: int = 42, tz: str = "Asia/Ho_Chi_Minh",
             is_exam_day: Callable[[date], bool] = lambda d: False) -> pd.DataFrame:
    """Hourly rows for the `days` days up to the last full hour before `end` (default: now)."""
    zone_tz = ZoneInfo(tz)
    rng = np.random.default_rng(seed)
    end_local = (end or datetime.now(zone_tz)).astimezone(zone_tz).replace(minute=0, second=0, microsecond=0)
    start_local = end_local - timedelta(days=days)
    hours = int((end_local - start_local).total_seconds() // 3600)
    peaks = {z: _peak(z) for z in ZONES}

    rows = []
    for zone in ZONES:
        noise = 0.0
        day_factor = 1.0
        current_day = None
        for i in range(hours):
            t = start_local + timedelta(hours=i)
            d = t.date()
            if d != current_day:
                current_day = d
                day_factor = float(np.clip(rng.normal(1.0, 0.06), 0.8, 1.2))
            h = t.hour + 0.5  # the bucket covers [hour, hour + 1)
            exam = is_exam_day(d)
            noise = 0.6 * noise + rng.normal(0, 0.025)  # AR(1) hourly noise

            if exam:
                core = open_factor(h)
                if core > 0:
                    # Exam period: 85-98 % through the day, following the usual daily rhythm.
                    shape = weekday_shape(zone, h) / peaks[zone]
                    rate = core * (0.85 + 0.13 * min(shape * 1.15, 1.0)) + rng.normal(0, 0.012)
                    if core >= 0.999:
                        rate = float(np.clip(rate, 0.85, 0.98))
                else:
                    rate = 0.0
            else:
                rate = weekday_shape(zone, h)
                rate *= WEEKEND_FACTOR.get(d.weekday(), 1.0)
                if _is_summer(d):
                    rate *= SUMMER_FACTOR
                rate = rate * day_factor + noise * open_factor(h)
            rate = float(np.clip(rate, 0.0, 0.99)) if open_factor(h) > 0 else 0.0

            rows.append((zone, t.astimezone(ZoneInfo("UTC")), round(rate, 4), d.isoweekday(), d.weekday() >= 5, exam))

    df = pd.DataFrame(rows, columns=["zone_code", "bucket_hour", "avg_occupancy_rate", "day_of_week", "is_weekend",
                                     "is_exam_period"])
    df["bucket_hour"] = pd.to_datetime(df["bucket_hour"], utc=True)
    return df


def main() -> None:  # python -m app.forecast.synthetic --days 180 --write-db
    import argparse

    from app.config import Settings
    from app.db import PostgresStatsRepository

    parser = argparse.ArgumentParser(description="Generate synthetic hourly occupancy history")
    parser.add_argument("--days", type=int, default=180)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--write-db", action="store_true", help="upsert into hourly_occupancy_stats")
    parser.add_argument("--csv", help="also write a CSV file")
    args = parser.parse_args()

    settings = Settings()
    df = generate(args.days, seed=args.seed, tz=settings.timezone, is_exam_day=settings.is_exam_day)
    print(f"generated {len(df)} rows: {df.bucket_hour.min()} .. {df.bucket_hour.max()}")
    print(df.groupby("zone_code")["avg_occupancy_rate"].describe().round(3))
    if args.csv:
        df.to_csv(args.csv, index=False)
    if args.write_db:
        n = PostgresStatsRepository.from_url(settings.db_url).upsert(df)
        print(f"upserted {n} rows into hourly_occupancy_stats")


if __name__ == "__main__":
    main()
