"""Forecast API logic: recursive multi-hour prediction + Redis caching."""

from __future__ import annotations

import logging
from datetime import datetime, timedelta
from typing import Any

import numpy as np
import pandas as pd

from app.cache import ForecastCache
from app.config import Settings
from app.db import StatsRepository
from app.forecast.features import FEATURES, LAGS, ZONES, calendar_features, zone_series
from app.forecast.training import BASELINE_KEY, GBM_KEY, ModelStore

log = logging.getLogger(__name__)
MAX_HOURS = 168


class UnknownZone(ValueError):
    pass


def level_of(rate: float) -> str:
    if rate < 0.40:
        return "LOW"
    if rate < 0.75:
        return "MEDIUM"
    return "HIGH"


class ForecastService:
    def __init__(self, settings: Settings, repo: StatsRepository, cache: ForecastCache, models: ModelStore):
        self.settings = settings
        self.repo = repo
        self.cache = cache
        self.models = models

    # ------------------------------------------------------------------ public

    def zone_codes(self) -> list[str]:
        try:
            return [z.code for z in self.repo.zones()]
        except Exception:  # noqa: BLE001 - DB down: fall back to the known layout
            return list(ZONES)

    def forecast(self, zone_code: str, hours: int = 24, now: datetime | None = None) -> dict[str, Any]:
        zone = zone_code.strip().upper()
        if zone not in self.zone_codes():
            raise UnknownZone(zone_code)
        hours = max(1, min(int(hours), MAX_HOURS))
        now_local = (now or datetime.now(self.settings.tz)).astimezone(self.settings.tz)
        start = now_local.replace(minute=0, second=0, microsecond=0)
        day_key = start.date().isoformat()

        cached = self.cache.get(zone, day_key)
        if cached:
            points = [p for p in cached["points"] if p["time"] >= start.isoformat()]
            if len(points) >= hours:
                return self._response(zone, cached, points[:hours], hit=True, day_key=day_key)

        horizon = max(hours, self.settings.forecast_horizon_hours)
        computed = self._compute(zone, start, horizon)
        self.cache.set(zone, day_key, computed)
        return self._response(zone, computed, computed["points"][:hours], hit=False, day_key=day_key)

    def at(self, zone_code: str, target: datetime, now: datetime | None = None) -> dict[str, Any]:
        """The forecast point for one future hour (used by the chat tool)."""
        now_local = (now or datetime.now(self.settings.tz)).astimezone(self.settings.tz)
        start = now_local.replace(minute=0, second=0, microsecond=0)
        target = target.astimezone(self.settings.tz).replace(minute=0, second=0, microsecond=0)
        needed = int((target - start).total_seconds() // 3600) + 1
        result = self.forecast(zone_code, max(needed, 1), now=now_local)
        for p in result["points"]:
            if p["time"] == target.isoformat():
                return {**p, "zoneCode": result["zoneCode"], "model": result["model"]}
        return {**result["points"][0], "zoneCode": result["zoneCode"], "model": result["model"]}

    def invalidate(self) -> int:
        return self.cache.clear()

    # ------------------------------------------------------------------ internals

    def _response(self, zone: str, data: dict[str, Any], points: list[dict], hit: bool, day_key: str) -> dict[str, Any]:
        return {
            "zoneCode": zone,
            "generatedAt": data["generatedAt"],
            "model": data["model"],
            "rmse": data.get("rmse"),
            "hours": len(points),
            "cache": {"hit": hit, "key": f"cache:forecast:zone:{zone}:{day_key}",
                      "ttlSeconds": self.cache.ttl_of(zone, day_key)},
            "points": points,
        }

    def _compute(self, zone: str, start: datetime, hours: int) -> dict[str, Any]:
        tz = self.settings.timezone
        history = self.repo.load_history(since=(start - timedelta(days=9)).astimezone(self.settings.tz))
        rate, _ = zone_series(history, zone, tz)
        bundle = self.models.bundle()
        model_key = GBM_KEY if bundle else BASELINE_KEY
        rmse = float(bundle["rmse"]) if bundle else 0.12

        # Series of known values (+ predictions as we go) on a gap-free hourly index up to the horizon.
        end = start + timedelta(hours=hours - 1)
        first = rate.index.min() if not rate.empty else start - timedelta(days=8)
        index = pd.date_range(min(first, start), end, freq="h", tz=tz)
        series = rate.reindex(index)
        last_known = rate.last_valid_index() if not rate.empty else None

        # Predict every hour after the last known value (fills small gaps before `start` too).
        begin = start if last_known is None else min(start, last_known + pd.Timedelta(hours=1))
        points = []
        for t in pd.date_range(begin, end, freq="h", tz=tz):
            if not np.isnan(series.get(t, np.nan)) and t < start:
                continue
            value = self._predict_one(series, t, zone, bundle)
            series.loc[t] = value
            if t >= start:
                points.append({
                    "time": t.isoformat(),
                    "hour": t.hour,
                    "predictedRate": round(value, 4),
                    "predictedPercent": int(round(value * 100)),
                    "lower": round(max(0.0, value - rmse), 4),
                    "upper": round(min(1.0, value + rmse), 4),
                    "level": level_of(value),
                })
        return {"generatedAt": datetime.now(self.settings.tz).isoformat(timespec="seconds"), "model": model_key,
                "rmse": round(rmse, 4), "startTime": start.isoformat(), "points": points}

    def _predict_one(self, series: pd.Series, t: pd.Timestamp, zone: str, bundle: dict | None) -> float:
        def at(hours_back: int) -> float:
            return float(series.get(t - pd.Timedelta(hours=hours_back), np.nan))

        lags = {lag: at(lag) for lag in LAGS}
        known = [v for v in lags.values() if not np.isnan(v)]
        baseline = float(np.mean(known)) if known else 0.0
        if bundle is None:
            return float(np.clip(baseline, 0, 1))

        window = series[(series.index > t - pd.Timedelta(hours=48)) & (series.index <= t - pd.Timedelta(hours=24))]
        row = calendar_features(pd.DatetimeIndex([t]))
        row["is_exam_period"] = int(self.settings.is_exam_day(t.date()))
        for z in ZONES:
            row[f"zone_{z}"] = int(z == zone)
        row["lag_24"], row["lag_48"], row["lag_168"] = lags[24], lags[48], lags[168]
        row["same_hour_mean_7d"] = baseline if known else np.nan
        row["roll_mean_24_lag24"] = window.mean() if window.notna().sum() >= 12 else np.nan
        row["roll_max_24_lag24"] = window.max() if window.notna().sum() >= 12 else np.nan
        value = float(bundle["model"].predict(row[FEATURES])[0])
        # The library is closed at night: never forecast occupancy outside opening hours.
        if t.hour < 7 or t.hour >= 22:
            value = 0.0
        return float(np.clip(value, 0, 1))
