from datetime import datetime
from zoneinfo import ZoneInfo

import numpy as np

from app.config import Settings
from app.forecast import synthetic
from app.forecast.training import BASELINE_KEY, GBM_KEY, ModelStore, regression_metrics, train

TZ = ZoneInfo("Asia/Ho_Chi_Minh")


def _local(df):
    out = df.assign(t=df.bucket_hour.dt.tz_convert("Asia/Ho_Chi_Minh"))
    out["h"] = out.t.dt.hour
    return out


def test_generates_180_days_of_hourly_rows_for_three_zones():
    s = Settings(_env_file=None)
    df = synthetic.generate(180, end=datetime(2026, 11, 3, 17, tzinfo=TZ), is_exam_day=s.is_exam_day)
    assert len(df) == 180 * 24 * 3
    assert set(df.zone_code) == {"A", "B", "C"}
    assert df.avg_occupancy_rate.between(0, 0.99).all()
    assert set(df.day_of_week) == set(range(1, 8))


def test_daily_rhythm_weekend_zone_personality_and_exams():
    s = Settings(_env_file=None)
    loc = _local(synthetic.generate(180, end=datetime(2026, 11, 3, 17, tzinfo=TZ), is_exam_day=s.is_exam_day))
    regular = loc[~loc.is_exam_period]
    weekday = regular[~regular.is_weekend]
    by_hour = weekday.pivot_table(index="zone_code", columns="h", values="avg_occupancy_rate")

    assert (loc[loc.h.isin([0, 3, 5, 23])].avg_occupancy_rate == 0).all()  # closed at night
    for zone in "ABC":
        assert by_hour.loc[zone, 15] > by_hour.loc[zone, 12]  # afternoon peak above lunch dip
    assert by_hour.loc["A", 9] > by_hour.loc["C", 9]  # quiet zone fills first
    assert by_hour.loc["C", 15] > by_hour.loc["C", 9]  # group tables peak in the afternoon
    assert by_hour.loc["C", 15] > by_hour.loc["A", 15]

    open_hours = regular.h.between(8, 20)
    assert regular[open_hours & regular.is_weekend].avg_occupancy_rate.mean() < \
        regular[open_hours & ~regular.is_weekend].avg_occupancy_rate.mean() * 0.75

    exam = loc[loc.is_exam_period & loc.h.between(9, 20)]
    assert len(exam) > 0
    assert exam.avg_occupancy_rate.min() >= 0.85 and exam.avg_occupancy_rate.max() <= 0.98


def test_metrics_definitions():
    y = np.array([0.0, 0.5, 1.0, 0.8])
    p = np.array([0.1, 0.5, 0.9, 0.8])
    m = regression_metrics(y, p)
    assert m["mae"] == 0.05
    assert abs(m["rmse"] - np.sqrt(0.005)) < 1e-4
    assert m["mape"] == round(((0 + 0.1 + 0) / 3) * 100, 3)  # zero actual excluded from MAPE
    assert m["r2"] > 0.9


def test_lightgbm_beats_the_moving_average_baseline(history, tmp_path):
    result = train(history, "Asia/Ho_Chi_Minh", test_days=28)
    base, gbm = result.metrics["models"][BASELINE_KEY], result.metrics["models"][GBM_KEY]
    assert gbm["mae"] < base["mae"] and gbm["rmse"] < base["rmse"]
    assert gbm["r2"] > 0.9
    assert set(gbm) >= {"mae", "rmse", "mape", "r2", "label"}
    assert set(result.metrics["perZone"]) == {"A", "B", "C"}
    assert result.metrics["bestModel"] == GBM_KEY

    store = ModelStore(str(tmp_path))
    store.save(result)
    reloaded = ModelStore(str(tmp_path))
    assert reloaded.is_trained() and reloaded.metrics()["models"][GBM_KEY]["mae"] == gbm["mae"]


def test_forecast_24h_is_cached_in_redis_with_the_spec_key(forecasts, redis_client):
    first = forecasts.forecast("A", 24)
    assert first["cache"]["hit"] is False and first["model"] == "lightgbm"
    assert len(first["points"]) == 24
    assert all(0 <= p["predictedRate"] <= 1 and p["lower"] <= p["predictedRate"] <= p["upper"] for p in first["points"])
    assert all(p["predictedRate"] == 0 for p in first["points"] if p["hour"] < 7 or p["hour"] >= 22)

    day = datetime.now(TZ).date().isoformat()
    key = f"cache:forecast:zone:A:{day}"
    assert first["cache"]["key"] == key and redis_client.exists(key)
    assert 3500 < redis_client.ttl(key) <= 3600

    second = forecasts.forecast("A", 12)
    assert second["cache"]["hit"] is True
    assert second["points"] == first["points"][:12]

    assert forecasts.invalidate() >= 1 and not redis_client.exists(key)


def test_forecast_tracks_the_daily_shape(forecasts):
    points = forecasts.forecast("C", 48)["points"]
    by_hour = {}
    for p in points:
        by_hour.setdefault(p["hour"], []).append(p["predictedRate"])
    if 15 in by_hour and 12 in by_hour:
        assert max(by_hour[15]) > min(by_hour[12])
    assert any(p["level"] in ("MEDIUM", "HIGH") for p in points)
