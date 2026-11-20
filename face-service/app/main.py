"""FastAPI app (internal: only backend-core calls it, with X-Internal-Key).

    POST /v1/analyze   frames of one head-turn challenge -> liveness verdict + ArcFace embedding
    GET  /health
"""

from __future__ import annotations

import hmac
import logging
from contextlib import asynccontextmanager
from typing import Annotated, Any

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator, model_validator

from app import __version__
from app.config import Settings
from app.imaging import InvalidImage, decode_frame
from app.liveness import STEPS
from app.pipeline import FacePipeline

log = logging.getLogger("face-service")


class Frame(BaseModel):
    step: str
    image: str = Field(min_length=16)  # base64 JPEG/PNG (a data: URL is fine)


class AnalyzeRequest(BaseModel):
    steps: list[str] = Field(min_length=1, max_length=5)
    frames: list[Frame] = Field(min_length=1)

    @field_validator("steps")
    @classmethod
    def known_steps(cls, v: list[str]) -> list[str]:
        bad = [s for s in v if s not in STEPS]
        if bad:
            raise ValueError(f"unknown steps {bad}; allowed {list(STEPS)}")
        return v

    @model_validator(mode="after")
    def frames_match_steps(self) -> "AnalyzeRequest":
        bad = {f.step for f in self.frames} - set(self.steps)
        if bad:
            raise ValueError(f"frames reference steps not in the challenge: {sorted(bad)}")
        return self


def create_app(pipeline: FacePipeline | None = None, settings: Settings | None = None) -> FastAPI:
    settings = settings or (pipeline.s if pipeline else Settings())

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if app.state.pipeline is None:
            app.state.pipeline = FacePipeline.load(settings)
        log.info("face-service %s ready (models in %s)", __version__, settings.model_dir)
        yield

    app = FastAPI(title="face-service", version=__version__, lifespan=lifespan)
    app.state.pipeline = pipeline

    def internal(x_internal_key: Annotated[str | None, Header()] = None) -> None:
        if settings.internal_key and not hmac.compare_digest(x_internal_key or "", settings.internal_key):
            raise HTTPException(status_code=401, detail={"code": "UNAUTHENTICATED",
                                                         "detail": "missing or wrong X-Internal-Key"})

    @app.exception_handler(HTTPException)
    async def http_error(_: Request, exc: HTTPException):
        body = exc.detail if isinstance(exc.detail, dict) else {"code": "ERROR", "detail": exc.detail}
        return JSONResponse(status_code=exc.status_code, content={"status": exc.status_code, **body})

    @app.get("/health")
    def health(request: Request) -> dict[str, Any]:
        return {"status": "ok", "version": __version__, "modelsLoaded": request.app.state.pipeline is not None}

    @app.post("/v1/analyze", dependencies=[Depends(internal)])
    def analyze(body: AnalyzeRequest, request: Request) -> dict[str, Any]:
        if len(body.frames) > settings.max_frames:
            raise HTTPException(status_code=413, detail={"code": "TOO_MANY_FRAMES",
                                                         "detail": f"at most {settings.max_frames} frames"})
        try:
            frames = [(f.step, decode_frame(f.image, settings.max_frame_bytes)) for f in body.frames]
        except InvalidImage as exc:
            raise HTTPException(status_code=400, detail={"code": "INVALID_IMAGE", "detail": str(exc)}) from None
        result = request.app.state.pipeline.analyze(body.steps, frames)
        log.info("analyze: %s (%s) %d frames in %d ms", result["status"], result["reason"], len(frames),
                 result["latencyMs"])
        return result

    return app


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(name)s - %(message)s")


def __getattr__(name: str):  # `uvicorn app.main:app` builds the app lazily (tests import create_app only)
    if name == "app":
        globals()["app"] = create_app()
        return globals()["app"]
    raise AttributeError(name)
