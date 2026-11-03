from __future__ import annotations

import json
import threading
import time
from datetime import datetime, timedelta, timezone
from typing import Any
from zoneinfo import ZoneInfo

import fakeredis
import httpx
import jwt
import pytest

from app.agent.memory import SessionStore
from app.agent.service import ChatService
from app.cache import ForecastCache
from app.config import Settings
from app.db import InMemoryStatsRepository
from app.forecast import synthetic
from app.forecast.service import ForecastService
from app.forecast.training import ModelStore, train
from app.main import Services
from app.security import Caller

TZ = ZoneInfo("Asia/Ho_Chi_Minh")
SECRET = "change-me-dev-only-secret-at-least-32-bytes-long"


def make_token(sub: str = "11111111-1111-1111-1111-111111111111", role: str = "STUDENT", sc: str = "SV001") -> str:
    # backend-core (jjwt) signs a 48-byte secret with HS384
    now = int(time.time())
    return jwt.encode({"sub": sub, "sc": sc, "role": role, "iat": now, "exp": now + 3600}, SECRET.encode(), "HS384")


@pytest.fixture(scope="session")
def history():
    end = datetime.now(TZ).replace(minute=0, second=0, microsecond=0)
    s = Settings(_env_file=None)
    return synthetic.generate(180, end=end, seed=7, is_exam_day=s.is_exam_day)


@pytest.fixture(scope="session")
def trained(history, tmp_path_factory):
    model_dir = tmp_path_factory.mktemp("models")
    store = ModelStore(str(model_dir))
    store.save(train(history, "Asia/Ho_Chi_Minh", test_days=28))
    return str(model_dir)


@pytest.fixture
def settings(trained) -> Settings:
    return Settings(_env_file=None, model_dir=trained, backend_url="http://backend.test", llm_provider="deterministic",
                    test_days=28)


@pytest.fixture
def redis_client():
    return fakeredis.FakeRedis(decode_responses=True)


@pytest.fixture
def repo(history) -> InMemoryStatsRepository:
    r = InMemoryStatsRepository()
    r.upsert(history)
    return r


@pytest.fixture
def forecasts(settings, repo, redis_client) -> ForecastService:
    return ForecastService(settings, repo, ForecastCache(redis_client, 3600), ModelStore(settings.model_dir))


class FakeBackend:
    """Minimal backend-core /api/v1/ai-tools/* with real-looking payloads and status codes."""

    SEATS = [
        # code, zone, quiet, power, type
        ("A-02", "A", True, False, "SINGLE"),
        ("A-03", "A", True, True, "SINGLE"),
        ("A-05", "A", True, True, "SINGLE"),
        ("B-04", "B", False, True, "SINGLE"),
        ("C-02", "C", False, False, "GROUP_TABLE"),
    ]

    def __init__(self, token: str):
        self.token = token
        self.taken: set[str] = set()
        self.reserved_by_user: str | None = None
        self.banned = False
        self.requests: list[httpx.Request] = []
        self.lock = threading.Lock()

    def transport(self) -> httpx.MockTransport:
        return httpx.MockTransport(self.handle)

    def _problem(self, status: int, code: str, detail: str) -> httpx.Response:
        return httpx.Response(status, json={"status": status, "code": code, "detail": detail})

    def handle(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        if request.headers.get("Authorization") != f"Bearer {self.token}":
            return self._problem(401, "UNAUTHENTICATED", "bad token")
        path = request.url.path
        if path == "/api/v1/ai-tools/optimal-seats":
            q = request.url.params
            out = []
            for code, zone, quiet, power, stype in self.SEATS:
                if code in self.taken:
                    continue
                if q.get("isQuietZone") == "true" and not quiet:
                    continue
                if q.get("hasPowerOutlet") == "true" and not power:
                    continue
                if q.get("seatType") and q.get("seatType") != stype:
                    continue
                score = (3 if q.get("zonePreference") == zone else 0) + (1 if power else 0)
                out.append({"seat": {"seatCode": code, "zoneCode": zone, "seatType": stype, "hasPowerOutlet": power,
                                     "quietZone": quiet, "status": "AVAILABLE"},
                            "score": score, "zoneOccupancyRate": 0.35, "reasons": []})
            out.sort(key=lambda r: (-r["score"], r["seat"]["seatCode"]))
            return httpx.Response(200, json=out[: int(q.get("limit", 5))])
        if path == "/api/v1/ai-tools/occupancy":
            zones = [request.url.params["zoneCode"]] if "zoneCode" in request.url.params else ["A", "B", "C"]
            return httpx.Response(200, json=[{"zone": {"code": z, "totalSeats": 20, "available": 11, "reserved": 1,
                                                       "occupied": 7, "away": 1, "occupancyRate": 0.4},
                                              "last24Hours": []} for z in zones])
        if path == "/api/v1/ai-tools/reservations" and request.method == "POST":
            seat = json.loads(request.content)["seatCode"]
            with self.lock:
                if self.banned:
                    return self._problem(403, "USER_BANNED", "Advance booking is suspended until 2026-11-06")
                if self.reserved_by_user:
                    return self._problem(409, "USER_HAS_ACTIVE_RESERVATION", "You already have an open reservation")
                if seat not in {s[0] for s in self.SEATS}:
                    return self._problem(404, "SEAT_NOT_FOUND", f"Seat {seat} does not exist")
                if seat in self.taken:
                    return self._problem(409, "SEAT_NOT_AVAILABLE", f"Seat {seat} is RESERVED")
                self.taken.add(seat)
                self.reserved_by_user = seat
            expire = (datetime.now(timezone.utc) + timedelta(minutes=15)).isoformat().replace("+00:00", "Z")
            return httpx.Response(201, json={"id": "r-" + seat, "seatCode": seat, "zoneCode": seat[0],
                                             "status": "PENDING", "bookingSource": "AI_CHATBOT",
                                             "expireAt": expire, "secondsUntilExpiry": 900})
        return self._problem(404, "NOT_FOUND", path)


@pytest.fixture
def token() -> str:
    return make_token()


@pytest.fixture
def caller(token) -> Caller:
    return Caller(user_id="11111111-1111-1111-1111-111111111111", student_code="SV001", role="STUDENT", token=token)


@pytest.fixture
def backend(token) -> FakeBackend:
    return FakeBackend(token)


@pytest.fixture
def chat_service(settings, forecasts, redis_client, backend) -> ChatService:
    return ChatService(settings, forecasts, SessionStore(redis_client, 600), provider=None,
                       backend_transport=backend.transport())


@pytest.fixture
def services(settings, repo, forecasts, redis_client, chat_service) -> Services:
    return Services(settings, repo, forecasts.cache, forecasts.models, forecasts, chat_service, threading.Lock())


def tool_names(resp: dict[str, Any]) -> list[str]:
    return [c["name"] for c in resp["toolCalls"]]
