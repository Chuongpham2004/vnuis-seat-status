"""FastAPI app.

    GET  /api/v1/ai/forecast?zoneCode=A&hours=24   forecast (cached in Redis 1 h)
    GET  /api/v1/ai/metrics                        MAE / RMSE / MAPE / R2 of baseline vs. LightGBM
    POST /api/v1/ai/chat                           assistant (student JWT required)
    POST /api/v1/ai/admin/synthetic                seed synthetic history (ADMIN)
    POST /api/v1/ai/admin/train                    retrain + clear the forecast cache (ADMIN)
    GET  /health
"""


import logging
import threading
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Annotated, Any

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app import __version__
from app.agent.llm import make_provider
from app.agent.memory import SessionStore
from app.agent.service import ChatService
from app.cache import ForecastCache
from app.config import Settings
from app.db import PostgresStatsRepository, StatsRepository
from app.forecast import synthetic
from app.forecast.service import ForecastService, UnknownZone
from app.forecast.training import ModelStore, train
from app.security import Caller, caller_from_header

log = logging.getLogger("ai-service")


@dataclass
class Services:
    settings: Settings
    repo: StatsRepository
    cache: ForecastCache
    models: ModelStore
    forecasts: ForecastService
    chat: ChatService
    lock: threading.Lock

    def seed(self, days: int, seed: int = 42) -> int:
        df = synthetic.generate(days, seed=seed, tz=self.settings.timezone, is_exam_day=self.settings.is_exam_day)
        return self.repo.upsert(df)

    def train(self) -> dict[str, Any]:
        with self.lock:
            result = train(self.repo.load_history(), self.settings.timezone, self.settings.test_days)
            self.models.save(result)
            self.forecasts.invalidate()
            return result.metrics

    def bootstrap(self) -> None:
        """Make a fresh `docker compose up` usable: seed if the table is (almost) empty, train if no model."""
        try:
            if self.repo.count() < 24 * 14 * 3:
                n = self.seed(self.settings.synthetic_days)
                log.info("bootstrap: seeded %d synthetic rows", n)
            if not self.models.is_trained():
                m = self.train()
                log.info("bootstrap: trained, LightGBM MAE %.4f vs baseline %.4f",
                         m["models"]["lightgbm"]["mae"], m["models"]["baseline_moving_average"]["mae"])
        except Exception:  # noqa: BLE001
            log.exception("bootstrap failed (the API still serves; retry with POST /api/v1/ai/admin/train)")


def build_services(settings: Settings) -> Services:
    repo = PostgresStatsRepository.from_url(settings.db_url)
    cache = ForecastCache.from_url(settings.redis_url, settings.forecast_cache_ttl)
    models = ModelStore(settings.model_dir)
    forecasts = ForecastService(settings, repo, cache, models)
    sessions = SessionStore(cache.client, settings.chat_session_ttl)
    chat = ChatService(settings, forecasts, sessions, make_provider(settings))
    return Services(settings, repo, cache, models, forecasts, chat, threading.Lock())


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)
    sessionId: str | None = Field(default=None, max_length=64)


class SyntheticRequest(BaseModel):
    days: int = Field(default=180, ge=8, le=730)
    seed: int = 42


def create_app(services: Services | None = None, settings: Settings | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        svc = app.state.services
        if svc.settings.auto_bootstrap:
            threading.Thread(target=svc.bootstrap, name="bootstrap", daemon=True).start()
        log.info("ai-service %s ready - chat engine: %s", __version__, svc.chat.engine_name)
        yield

    app = FastAPI(title="ai-service", version=__version__, lifespan=lifespan)
    app.state.services = services or build_services(settings or Settings())

    def svc(request: Request) -> Services:
        return request.app.state.services

    def caller(request: Request, authorization: Annotated[str | None, Header()] = None) -> Caller:
        return caller_from_header(authorization, request.app.state.services.settings.jwt_secret)

    def admin(user: Annotated[Caller, Depends(caller)]) -> Caller:
        if not user.is_admin:
            raise HTTPException(status_code=403, detail={"code": "ACCESS_DENIED", "detail": "ADMIN role required"})
        return user

    @app.exception_handler(HTTPException)
    async def http_error(_: Request, exc: HTTPException):
        body = exc.detail if isinstance(exc.detail, dict) else {"code": "ERROR", "detail": exc.detail}
        return JSONResponse(status_code=exc.status_code, content={"status": exc.status_code, **body},
                            headers=getattr(exc, "headers", None))

    @app.get("/health")
    def health(s: Annotated[Services, Depends(svc)]) -> dict[str, Any]:
        return {"status": "ok", "version": __version__, "chatEngine": s.chat.engine_name,
                "modelTrained": s.models.is_trained()}

    @app.get("/api/v1/ai/forecast")
    def forecast(s: Annotated[Services, Depends(svc)],
                 zoneCode: Annotated[str | None, Query(max_length=4)] = None,
                 hours: Annotated[int, Query(ge=1, le=168)] = 24) -> dict[str, Any]:
        try:
            if zoneCode:
                return s.forecasts.forecast(zoneCode, hours)
            return {"zones": [s.forecasts.forecast(z, hours) for z in s.forecasts.zone_codes()]}
        except UnknownZone:
            raise HTTPException(status_code=404, detail={"code": "ZONE_NOT_FOUND",
                                                         "detail": f"Zone {zoneCode} does not exist"}) from None

    @app.get("/api/v1/ai/metrics")
    def metrics(s: Annotated[Services, Depends(svc)]) -> dict[str, Any]:
        m = s.models.metrics()
        if m is None:
            raise HTTPException(status_code=404, detail={"code": "MODEL_NOT_TRAINED",
                                                         "detail": "Train first: POST /api/v1/ai/admin/train"})
        return m

    @app.post("/api/v1/ai/chat")
    def chat(body: ChatRequest, s: Annotated[Services, Depends(svc)],
             user: Annotated[Caller, Depends(caller)]) -> dict[str, Any]:
        return s.chat.chat(user, body.message, body.sessionId)

    @app.post("/api/v1/ai/admin/synthetic")
    def seed(body: SyntheticRequest, s: Annotated[Services, Depends(svc)],
             _: Annotated[Caller, Depends(admin)]) -> dict[str, Any]:
        rows = s.seed(body.days, body.seed)
        return {"rows": rows, "days": body.days, "totalRows": s.repo.count()}

    @app.post("/api/v1/ai/admin/train")
    def retrain(s: Annotated[Services, Depends(svc)], _: Annotated[Caller, Depends(admin)]) -> dict[str, Any]:
        try:
            return s.train()
        except ValueError as exc:
            raise HTTPException(status_code=409, detail={"code": "NOT_ENOUGH_DATA", "detail": str(exc)}) from None

    return app


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(name)s - %(message)s")


def __getattr__(name: str):  # `uvicorn app.main:app` builds the app lazily (tests import create_app only)
    if name == "app":
        globals()["app"] = create_app()
        return globals()["app"]
    raise AttributeError(name)
