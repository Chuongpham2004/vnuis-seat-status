"""Settings from environment variables (names match infra/docker-compose.yml)."""

from __future__ import annotations

from datetime import date
from functools import cached_property
from zoneinfo import ZoneInfo

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # ---- data stores ----
    # ai_reader: read-only everywhere + write on hourly_occupancy_stats (Flyway V3).
    db_url: str = "postgresql+psycopg://ai_reader:ai_reader_dev_pw@localhost:5433/library"
    redis_url: str = "redis://localhost:6379/0"
    backend_url: str = "http://localhost:8080"

    # Same HMAC secret as backend-core, used to verify the caller's JWT.
    jwt_secret: str = "change-me-dev-only-secret-at-least-32-bytes-long"

    # ---- forecasting ----
    timezone: str = "Asia/Ho_Chi_Minh"
    model_dir: str = "models"
    exam_periods: str = "2026-05-25/2026-06-14,2026-10-19/2026-10-30,2026-12-14/2027-01-10"
    forecast_cache_ttl: int = 3600
    forecast_horizon_hours: int = 48  # computed once, cached, sliced per request
    test_days: int = 28  # hold-out window for MAE/RMSE/MAPE/R2
    synthetic_days: int = 180
    auto_bootstrap: bool = False  # on startup: seed synthetic history if empty, train if no model

    # ---- assistant ----
    # auto = first configured of anthropic, openai, gemini, ollama; otherwise the deterministic engine.
    llm_provider: str = "auto"
    llm_timeout_seconds: float = 30.0
    anthropic_api_key: str | None = None
    anthropic_model: str = "claude-sonnet-5-5"
    openai_api_key: str | None = None
    openai_base_url: str | None = None
    openai_model: str = "gpt-4o-mini"
    gemini_api_key: str | None = None
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta/openai/"
    gemini_model: str = "gemini-2.0-flash"
    ollama_base_url: str | None = None  # e.g. http://localhost:11434/v1
    ollama_model: str = "qwen2.5:7b"
    chat_session_ttl: int = 7200

    @cached_property
    def tz(self) -> ZoneInfo:
        return ZoneInfo(self.timezone)

    @cached_property
    def exam_ranges(self) -> list[tuple[date, date]]:
        ranges = []
        for part in self.exam_periods.split(","):
            part = part.strip()
            if "/" in part:
                start, end = part.split("/", 1)
                ranges.append((date.fromisoformat(start.strip()), date.fromisoformat(end.strip())))
        return ranges

    def is_exam_day(self, d: date) -> bool:
        return any(start <= d <= end for start, end in self.exam_ranges)
