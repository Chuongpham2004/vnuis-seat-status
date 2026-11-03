"""HTTP control API for the live demo (default http://127.0.0.1:8090, docs at /docs)."""

from __future__ import annotations

from typing import Any, Callable, Literal

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from simulator.manager import SimulatorManager, UnknownSeat


class ModeRequest(BaseModel):
    mode: Literal["ambient", "manual"]


def create_app(manager: SimulatorManager) -> FastAPI:
    app = FastAPI(title="Seat simulator control API", version="0.3.0")

    def seat_action(fn: Callable[[str], dict[str, Any]], code: str) -> dict[str, Any]:
        try:
            return fn(code)
        except UnknownSeat:
            raise HTTPException(status_code=404, detail=f"Seat {code} is not simulated") from None

    @app.get("/health")
    def health() -> dict[str, Any]:
        st = manager.status()
        return {"status": "ok", "seats": st["seats"], "connected": st["connected"]}

    @app.get("/status")
    def status() -> dict[str, Any]:
        return manager.status()

    @app.get("/seats")
    def seats() -> list[dict[str, Any]]:
        return manager.status()["seatList"]

    @app.get("/seats/{code}")
    def seat(code: str) -> dict[str, Any]:
        return seat_action(lambda c: manager.seat(c).snapshot(), code)

    @app.post("/seats/{code}/sit")
    def sit(code: str) -> dict[str, Any]:
        """Publish < 50 cm (someone sits down). The seat is pinned to manual control."""
        return seat_action(manager.sit, code)

    @app.post("/seats/{code}/stand")
    def stand(code: str) -> dict[str, Any]:
        """Publish > 50 cm (the student leaves). The seat is pinned to manual control."""
        return seat_action(manager.stand, code)

    @app.post("/seats/{code}/disconnect")
    def disconnect(code: str) -> dict[str, Any]:
        """Drop the connection without DISCONNECT -> the broker publishes the retained OFFLINE Last Will."""
        return seat_action(manager.disconnect, code)

    @app.post("/seats/{code}/reconnect")
    def reconnect(code: str) -> dict[str, Any]:
        return seat_action(manager.reconnect, code)

    @app.post("/seats/{code}/release")
    def release(code: str) -> dict[str, Any]:
        """Hand the seat back to ambient behaviour."""
        return seat_action(manager.release, code)

    @app.post("/mode")
    def set_mode(body: ModeRequest) -> dict[str, str]:
        return {"mode": manager.set_mode(body.mode)}

    @app.post("/scenario/{name}")
    def scenario(name: str) -> dict[str, Any]:
        try:
            return manager.scenario(name)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from None

    return app
