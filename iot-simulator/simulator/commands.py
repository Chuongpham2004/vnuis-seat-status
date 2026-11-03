"""Text commands shared by the interactive console (`run --interactive`) and `shell`.

    sit A-02 | stand A-02 | disconnect A-02 | reconnect A-02 | release A-02
    status [A-02] | mode ambient|manual | scenario rush_hour|mass_leave|reset | help
"""

from __future__ import annotations

import json
from typing import Any, Protocol

import httpx

HELP = """commands:
  sit <seat>          someone sits down   (publishes < 50 cm)
  stand <seat>        the student leaves  (publishes > 50 cm)
  disconnect <seat>   kill the device      (broker publishes Last Will OFFLINE)
  reconnect <seat>    bring it back ONLINE
  release <seat>      give the seat back to ambient behaviour
  status [seat]       summary, or one seat
  mode ambient|manual
  scenario rush_hour|mass_leave|reset
  help | quit"""

SEAT_VERBS = ("sit", "stand", "disconnect", "reconnect", "release")


class Backend(Protocol):
    def seat_command(self, verb: str, seat: str) -> dict[str, Any]: ...
    def status(self, seat: str | None = None) -> dict[str, Any]: ...
    def mode(self, mode: str) -> dict[str, Any]: ...
    def scenario(self, name: str) -> dict[str, Any]: ...


class ManagerBackend:
    """Calls the in-process manager directly."""

    def __init__(self, manager):
        self.m = manager

    def seat_command(self, verb: str, seat: str) -> dict[str, Any]:
        return getattr(self.m, verb)(seat)

    def status(self, seat: str | None = None) -> dict[str, Any]:
        if seat:
            return self.m.seat(seat).snapshot()
        st = self.m.status()
        st.pop("seatList")
        return st

    def mode(self, mode: str) -> dict[str, Any]:
        return {"mode": self.m.set_mode(mode)}

    def scenario(self, name: str) -> dict[str, Any]:
        return self.m.scenario(name)


class HttpBackend:
    """Talks to a running simulator's control API."""

    def __init__(self, base_url: str, timeout: float = 5.0):
        self.http = httpx.Client(base_url=base_url.rstrip("/"), timeout=timeout)

    def _check(self, resp: httpx.Response) -> dict[str, Any]:
        if resp.status_code >= 400:
            detail = resp.json().get("detail", resp.text) if resp.headers.get("content-type", "").startswith("application/json") else resp.text
            raise ValueError(f"{resp.status_code}: {detail}")
        return resp.json()

    def seat_command(self, verb: str, seat: str) -> dict[str, Any]:
        return self._check(self.http.post(f"/seats/{seat}/{verb}"))

    def status(self, seat: str | None = None) -> dict[str, Any]:
        if seat:
            return self._check(self.http.get(f"/seats/{seat}"))
        st = self._check(self.http.get("/status"))
        st.pop("seatList", None)
        return st

    def mode(self, mode: str) -> dict[str, Any]:
        return self._check(self.http.post("/mode", json={"mode": mode}))

    def scenario(self, name: str) -> dict[str, Any]:
        return self._check(self.http.post(f"/scenario/{name}"))


def execute(line: str, backend: Backend) -> str:
    """Runs one command line and returns the text to print. Raises SystemExit on quit."""
    parts = line.strip().split()
    if not parts:
        return ""
    verb, args = parts[0].lower(), parts[1:]
    try:
        if verb in ("quit", "exit"):
            raise SystemExit(0)
        if verb in ("help", "?"):
            return HELP
        if verb in SEAT_VERBS:
            if len(args) != 1:
                return f"usage: {verb} <seat>   e.g. {verb} A-02"
            return _fmt(backend.seat_command(verb, args[0].upper()))
        if verb == "status":
            return _fmt(backend.status(args[0].upper() if args else None))
        if verb == "mode" and len(args) == 1:
            return _fmt(backend.mode(args[0].lower()))
        if verb == "scenario" and len(args) == 1:
            return _fmt(backend.scenario(args[0].lower()))
        return f"unknown command: {line.strip()}\n{HELP}"
    except SystemExit:
        raise
    except KeyError as exc:
        return f"error: seat {exc.args[0] if exc.args else ''} is not simulated"
    except (ValueError, httpx.HTTPError) as exc:
        return f"error: {exc}"


def _fmt(obj: dict[str, Any]) -> str:
    return json.dumps(obj, ensure_ascii=False)
