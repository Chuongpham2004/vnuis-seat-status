"""Feature engineering shared by training and forecasting.

Every lag is at least 24 h old, so a row for hour t only needs data up to t - 24 h:
the model is a direct 1-24 h ahead forecaster. Longer horizons feed predictions back in.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

ZONES = ("A", "B", "C")
LAGS = (24, 48, 72, 96, 120, 144, 168)

FEATURES = [
    "hour_sin", "hour_cos", "dow_sin", "dow_cos",  # cyclical time
    "is_weekend", "is_exam_period",
    "zone_A", "zone_B", "zone_C",
    "lag_24", "lag_48", "lag_168",  # same hour yesterday / 2 days ago / last week
    "same_hour_mean_7d",  # = the baseline moving average
    "roll_mean_24_lag24",  # mean of the 24 h window ending 24 h ago
    "roll_max_24_lag24",
]
BASELINE_FEATURE = "same_hour_mean_7d"


def calendar_features(local_times: pd.DatetimeIndex) -> pd.DataFrame:
    hour = local_times.hour.to_numpy()
    dow = local_times.dayofweek.to_numpy()  # Monday=0
    return pd.DataFrame({
        "hour_sin": np.sin(2 * np.pi * hour / 24),
        "hour_cos": np.cos(2 * np.pi * hour / 24),
        "dow_sin": np.sin(2 * np.pi * dow / 7),
        "dow_cos": np.cos(2 * np.pi * dow / 7),
        "is_weekend": (dow >= 5).astype(int),
    }, index=local_times)


def zone_series(history: pd.DataFrame, zone: str, tz: str) -> tuple[pd.Series, pd.Series]:
    """Hourly occupancy and exam flag for one zone on a gap-free local-time index (missing = NaN)."""
    z = history[history["zone_code"] == zone]
    if z.empty:
        return pd.Series(dtype=float), pd.Series(dtype=float)
    idx = pd.DatetimeIndex(z["bucket_hour"]).tz_convert(tz)
    rate = pd.Series(z["avg_occupancy_rate"].astype(float).to_numpy(), index=idx).sort_index()
    exam = pd.Series(z["is_exam_period"].astype(int).to_numpy(), index=idx).sort_index()
    rate = rate[~rate.index.duplicated(keep="last")]
    exam = exam[~exam.index.duplicated(keep="last")]
    full = pd.date_range(rate.index.min(), rate.index.max(), freq="h", tz=tz)
    return rate.reindex(full), exam.reindex(full)


def lag_features(rate: pd.Series) -> pd.DataFrame:
    out = pd.DataFrame(index=rate.index)
    for lag in LAGS:
        out[f"lag_{lag}"] = rate.shift(lag)
    out["same_hour_mean_7d"] = out[[f"lag_{lag}" for lag in LAGS]].mean(axis=1)
    shifted = rate.shift(24)
    out["roll_mean_24_lag24"] = shifted.rolling(24, min_periods=12).mean()
    out["roll_max_24_lag24"] = shifted.rolling(24, min_periods=12).max()
    return out


def build_training_frame(history: pd.DataFrame, tz: str) -> pd.DataFrame:
    """One row per (zone, hour) with FEATURES, the target `y` and `zone_code`; rows without a full week of lags are dropped."""
    frames = []
    for zone in ZONES:
        rate, exam = zone_series(history, zone, tz)
        if rate.empty:
            continue
        f = calendar_features(rate.index)
        f["is_exam_period"] = exam.fillna(0).astype(int).to_numpy()
        for z in ZONES:
            f[f"zone_{z}"] = int(z == zone)
        f = f.join(lag_features(rate))
        f["y"] = rate
        f["zone_code"] = zone
        frames.append(f)
    if not frames:
        return pd.DataFrame(columns=FEATURES + ["y", "zone_code"])
    frame = pd.concat(frames)
    return frame.dropna(subset=["y", "lag_168", "same_hour_mean_7d"])
