"""Chat orchestration: pick the engine, keep the session, never book twice on a fallback."""

from __future__ import annotations

import logging
import uuid
from datetime import datetime
from typing import Any

import httpx

from app.agent.backend import BackendClient
from app.agent.deterministic import ChatResult, DeterministicAgent, booked_reply
from app.agent.llm import LLMAgent, LLMProvider
from app.agent.memory import SessionStore
from app.agent.text import detect_language
from app.agent.tools import ToolExecutor
from app.config import Settings
from app.forecast.service import ForecastService
from app.security import Caller

log = logging.getLogger(__name__)


class ChatService:
    def __init__(self, settings: Settings, forecasts: ForecastService, sessions: SessionStore,
                 provider: LLMProvider | None, backend_transport: httpx.BaseTransport | None = None):
        self.settings = settings
        self.forecasts = forecasts
        self.sessions = sessions
        self.provider = provider
        self.deterministic = DeterministicAgent(settings.tz)
        self.backend_transport = backend_transport

    @property
    def engine_name(self) -> str:
        return f"llm:{self.provider.name}" if self.provider else "deterministic"

    def chat(self, caller: Caller, message: str, session_id: str | None = None) -> dict[str, Any]:
        session_id = session_id or uuid.uuid4().hex
        session = self.sessions.load(caller.user_id, session_id)
        now = datetime.now(self.settings.tz)
        backend = BackendClient(self.settings.backend_url, caller.token, transport=self.backend_transport)
        executor = ToolExecutor(backend, self.forecasts, now=lambda: datetime.now(self.settings.tz))
        try:
            result = self._respond(message, executor, session, now)
        finally:
            backend.close()

        if result.suggested_seats:
            session.suggestions = result.suggested_seats
        if result.reservation:
            session.suggestions = []
        session.remember(message, result.reply)
        self.sessions.save(caller.user_id, session_id, session)

        return {
            "sessionId": session_id,
            "reply": result.reply,
            "language": result.language,
            "engine": result.engine,
            "intent": result.intent,
            "toolCalls": [c.as_dict() for c in result.tool_calls],
            "suggestedSeats": result.suggested_seats,
            "reservation": result.reservation,
        }

    def _respond(self, message: str, executor: ToolExecutor, session, now: datetime) -> ChatResult:
        if self.provider is not None:
            try:
                return LLMAgent(self.provider).respond(message, executor, session.history, now)
            except Exception as exc:  # noqa: BLE001 - any provider failure -> deterministic engine
                log.warning("LLM provider %s failed, using the deterministic engine: %s", self.provider.name, exc)
                booked = next((c for c in executor.trace if c.ok and c.name == "reserve_seat"), None)
                if booked:
                    # The booking already happened: confirm it instead of booking again.
                    language = detect_language(message)
                    return ChatResult(reply=booked_reply(booked.result, None, language, self.settings.tz),
                                      language=language, engine=f"deterministic (fallback from {self.provider.name})",
                                      tool_calls=list(executor.trace), reservation=booked.result)
                result = self.deterministic.respond(message, executor, session.suggestions, now)
                result.engine = f"deterministic (fallback from {self.provider.name})"
                return result
        return self.deterministic.respond(message, executor, session.suggestions, now)
