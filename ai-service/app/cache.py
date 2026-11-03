"""Redis cache for forecasts: cache:forecast:zone:{zone_code}:{YYYY-MM-DD} (TTL 1 h).

The cache is an optimisation only: every Redis error is logged and ignored.
"""

from __future__ import annotations

import json
import logging
from typing import Any

import redis

log = logging.getLogger(__name__)

KEY_PREFIX = "cache:forecast:zone:"


def forecast_key(zone_code: str, day: str) -> str:
    return f"{KEY_PREFIX}{zone_code}:{day}"


class ForecastCache:
    def __init__(self, client: "redis.Redis | None", ttl_seconds: int = 3600):
        self.client = client
        self.ttl = ttl_seconds

    @classmethod
    def from_url(cls, url: str, ttl_seconds: int) -> "ForecastCache":
        return cls(redis.Redis.from_url(url, decode_responses=True, socket_timeout=2, socket_connect_timeout=2),
                   ttl_seconds)

    def get(self, zone_code: str, day: str) -> dict[str, Any] | None:
        if self.client is None:
            return None
        try:
            raw = self.client.get(forecast_key(zone_code, day))
            return json.loads(raw) if raw else None
        except Exception as exc:  # noqa: BLE001
            log.warning("forecast cache read failed: %s", exc)
            return None

    def set(self, zone_code: str, day: str, value: dict[str, Any]) -> None:
        if self.client is None:
            return
        try:
            self.client.set(forecast_key(zone_code, day), json.dumps(value), ex=self.ttl)
        except Exception as exc:  # noqa: BLE001
            log.warning("forecast cache write failed: %s", exc)

    def ttl_of(self, zone_code: str, day: str) -> int | None:
        if self.client is None:
            return None
        try:
            return int(self.client.ttl(forecast_key(zone_code, day)))
        except Exception:  # noqa: BLE001
            return None

    def clear(self) -> int:
        """Drop every cached forecast (after retraining)."""
        if self.client is None:
            return 0
        try:
            keys = list(self.client.scan_iter(match=KEY_PREFIX + "*", count=200))
            return self.client.delete(*keys) if keys else 0
        except Exception as exc:  # noqa: BLE001
            log.warning("forecast cache clear failed: %s", exc)
            return 0
