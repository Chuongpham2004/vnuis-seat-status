"""Per-user chat sessions in Redis (ai:chat:session:{user}:{session}), with an in-process fallback."""

from __future__ import annotations

import json
import logging
import time
from dataclasses import asdict, dataclass, field
from typing import Any

log = logging.getLogger(__name__)
MAX_HISTORY = 12  # text turns kept for the LLM


@dataclass
class Session:
    suggestions: list[dict[str, Any]] = field(default_factory=list)
    history: list[dict[str, str]] = field(default_factory=list)  # [{"role": "user"|"assistant", "content": ...}]

    def remember(self, user_text: str, reply: str) -> None:
        self.history = (self.history + [{"role": "user", "content": user_text},
                                        {"role": "assistant", "content": reply}])[-MAX_HISTORY:]


class SessionStore:
    def __init__(self, redis_client=None, ttl_seconds: int = 7200):
        self.redis = redis_client
        self.ttl = ttl_seconds
        self._local: dict[str, tuple[float, str]] = {}

    @staticmethod
    def key(user_id: str, session_id: str) -> str:
        return f"ai:chat:session:{user_id}:{session_id}"

    def load(self, user_id: str, session_id: str) -> Session:
        raw = None
        k = self.key(user_id, session_id)
        if self.redis is not None:
            try:
                raw = self.redis.get(k)
            except Exception as exc:  # noqa: BLE001
                log.warning("session read failed: %s", exc)
        if raw is None and k in self._local and self._local[k][0] > time.time():
            raw = self._local[k][1]
        if not raw:
            return Session()
        data = json.loads(raw)
        return Session(suggestions=data.get("suggestions", []), history=data.get("history", []))

    def save(self, user_id: str, session_id: str, session: Session) -> None:
        raw = json.dumps(asdict(session), ensure_ascii=False)
        k = self.key(user_id, session_id)
        if self.redis is not None:
            try:
                self.redis.set(k, raw, ex=self.ttl)
                return
            except Exception as exc:  # noqa: BLE001
                log.warning("session write failed, keeping it in memory: %s", exc)
        self._local[k] = (time.time() + self.ttl, raw)
