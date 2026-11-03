"""Train and compare: seasonal moving-average baseline vs. LightGBM gradient boosting.

    python -m app.forecast.training            # train on hourly_occupancy_stats, write models/
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from lightgbm import LGBMRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

from app.forecast.features import BASELINE_FEATURE, FEATURES, ZONES, build_training_frame

log = logging.getLogger(__name__)

MODEL_FILE = "occupancy_lgbm.joblib"
METRICS_FILE = "metrics.json"
MAPE_MIN_ACTUAL = 0.05  # MAPE is undefined when the library is empty: evaluate open hours only

BASELINE_KEY = "baseline_moving_average"
GBM_KEY = "lightgbm"
MODEL_LABELS = {
    BASELINE_KEY: "Seasonal moving average (same hour, previous 7 days)",
    GBM_KEY: "LightGBM gradient boosting (cyclical time, exam flag, 24-168 h lags, rolling stats)",
}


def regression_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> dict[str, float]:
    y_true = np.asarray(y_true, dtype=float)
    y_pred = np.asarray(y_pred, dtype=float)
    open_hours = y_true >= MAPE_MIN_ACTUAL
    mape = float(np.mean(np.abs((y_true[open_hours] - y_pred[open_hours]) / y_true[open_hours])) * 100) \
        if open_hours.any() else float("nan")
    return {
        "mae": round(float(mean_absolute_error(y_true, y_pred)), 5),
        "rmse": round(float(np.sqrt(mean_squared_error(y_true, y_pred))), 5),
        "mape": round(mape, 3),
        "r2": round(float(r2_score(y_true, y_pred)), 5),
    }


def new_model(seed: int = 42) -> LGBMRegressor:
    return LGBMRegressor(n_estimators=600, learning_rate=0.03, num_leaves=31, min_child_samples=20,
                         subsample=0.8, subsample_freq=1, colsample_bytree=0.9, reg_lambda=1.0,
                         random_state=seed, verbose=-1)


@dataclass
class TrainResult:
    model: LGBMRegressor
    metrics: dict[str, Any]


def train(history: pd.DataFrame, tz: str, test_days: int = 28, seed: int = 42) -> TrainResult:
    frame = build_training_frame(history, tz)
    if frame.empty:
        raise ValueError("No usable history: need at least 8 days of hourly_occupancy_stats")
    cutoff = frame.index.max() - pd.Timedelta(days=test_days)
    train_df = frame[frame.index <= cutoff]
    test_df = frame[frame.index > cutoff]
    if len(train_df) < 24 * 7 or test_df.empty:
        raise ValueError(f"Not enough history for a {test_days}-day hold-out (rows: {len(frame)})")

    model = new_model(seed)
    model.fit(train_df[FEATURES], train_df["y"])

    y = test_df["y"].to_numpy()
    preds = {
        BASELINE_KEY: test_df[BASELINE_FEATURE].clip(0, 1).to_numpy(),
        GBM_KEY: np.clip(model.predict(test_df[FEATURES]), 0, 1),
    }
    overall = {k: {"label": MODEL_LABELS[k], **regression_metrics(y, p)} for k, p in preds.items()}
    per_zone: dict[str, Any] = {}
    for zone in ZONES:
        mask = (test_df["zone_code"] == zone).to_numpy()
        if mask.any():
            per_zone[zone] = {k: regression_metrics(y[mask], p[mask]) for k, p in preds.items()}

    base, gbm = overall[BASELINE_KEY], overall[GBM_KEY]
    importance = sorted(zip(FEATURES, model.booster_.feature_importance(importance_type="gain")),
                        key=lambda kv: kv[1], reverse=True)
    total_gain = sum(v for _, v in importance) or 1.0
    metrics = {
        "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "bestModel": GBM_KEY if gbm["mae"] <= base["mae"] else BASELINE_KEY,
        "trainRows": int(len(train_df)),
        "testRows": int(len(test_df)),
        "trainPeriod": {"from": train_df.index.min().isoformat(), "to": train_df.index.max().isoformat()},
        "testPeriod": {"from": test_df.index.min().isoformat(), "to": test_df.index.max().isoformat()},
        "horizon": "1-24 h ahead (all lag features are >= 24 h old)",
        "units": "occupancy rate 0..1 for MAE/RMSE; MAPE in % over open hours (actual >= 5 %)",
        "models": overall,
        "perZone": per_zone,
        "improvementPct": {
            "mae": round((base["mae"] - gbm["mae"]) / base["mae"] * 100, 2) if base["mae"] else None,
            "rmse": round((base["rmse"] - gbm["rmse"]) / base["rmse"] * 100, 2) if base["rmse"] else None,
        },
        "featureImportance": [{"feature": f, "share": round(float(g) / float(total_gain), 4)} for f, g in importance],
    }
    return TrainResult(model=model, metrics=metrics)


class ModelStore:
    """Persists the trained model + metrics in model_dir and keeps them loaded."""

    def __init__(self, model_dir: str):
        self.dir = Path(model_dir)
        self._bundle: dict[str, Any] | None = None
        self._metrics: dict[str, Any] | None = None

    def save(self, result: TrainResult) -> None:
        self.dir.mkdir(parents=True, exist_ok=True)
        bundle = {"model": result.model, "features": FEATURES,
                  "rmse": result.metrics["models"][GBM_KEY]["rmse"], "trainedAt": result.metrics["trainedAt"]}
        joblib.dump(bundle, self.dir / MODEL_FILE)
        (self.dir / METRICS_FILE).write_text(json.dumps(result.metrics, indent=2), encoding="utf-8")
        self._bundle, self._metrics = bundle, result.metrics

    def bundle(self) -> dict[str, Any] | None:
        if self._bundle is None and (self.dir / MODEL_FILE).exists():
            self._bundle = joblib.load(self.dir / MODEL_FILE)
        return self._bundle

    def metrics(self) -> dict[str, Any] | None:
        if self._metrics is None and (self.dir / METRICS_FILE).exists():
            self._metrics = json.loads((self.dir / METRICS_FILE).read_text(encoding="utf-8"))
        return self._metrics

    def is_trained(self) -> bool:
        return self.bundle() is not None


def main() -> None:
    from app.config import Settings
    from app.db import PostgresStatsRepository

    logging.basicConfig(level=logging.INFO)
    settings = Settings()
    history = PostgresStatsRepository.from_url(settings.db_url).load_history()
    result = train(history, settings.timezone, settings.test_days)
    ModelStore(settings.model_dir).save(result)
    m = result.metrics["models"]
    print(f"{'model':<26}{'MAE':>9}{'RMSE':>9}{'MAPE%':>9}{'R2':>9}")
    for key, v in m.items():
        print(f"{key:<26}{v['mae']:>9.4f}{v['rmse']:>9.4f}{v['mape']:>9.2f}{v['r2']:>9.4f}")
    print("improvement vs baseline:", result.metrics["improvementPct"])


if __name__ == "__main__":
    main()
