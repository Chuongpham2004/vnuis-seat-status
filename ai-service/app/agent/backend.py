"""HTTP client for backend-core's /api/v1/ai-tools/* endpoints, called with the student's own JWT."""

from __future__ import annotations

from typing import Any

import httpx


class BackendError(Exception):
    def __init__(self, status: int, code: str, detail: str):
        super().__init__(f"{status} {code}: {detail}")
        self.status = status
        self.code = code
        self.detail = detail

    def as_dict(self) -> dict[str, Any]:
        return {"status": self.status, "code": self.code, "detail": self.detail}


class BackendClient:
    def __init__(self, base_url: str, token: str, timeout: float = 10.0, transport: httpx.BaseTransport | None = None):
        self.http = httpx.Client(base_url=base_url.rstrip("/"), timeout=timeout, transport=transport,
                                 headers={"Authorization": f"Bearer {token}", "Accept": "application/json"})

    def close(self) -> None:
        self.http.close()

    def _send(self, method: str, path: str, **kwargs: Any) -> Any:
        try:
            resp = self.http.request(method, path, **kwargs)
        except httpx.HTTPError as exc:
            raise BackendError(503, "BACKEND_UNREACHABLE", str(exc)) from None
        if resp.status_code == 204:
            return None
        if resp.status_code >= 400:
            try:
                body = resp.json()
                code, detail = body.get("code", "ERROR"), body.get("detail", resp.text)
            except ValueError:
                code, detail = "ERROR", resp.text
            raise BackendError(resp.status_code, code, detail)
        return resp.json()

    def optimal_seats(self, zone_code: str | None = None, is_quiet_zone: bool | None = None,
                      has_power_outlet: bool | None = None, seat_type: str | None = None, limit: int = 3) -> list[dict]:
        params = {k: v for k, v in {
            "zonePreference": zone_code,
            "isQuietZone": _bool(is_quiet_zone),
            "hasPowerOutlet": _bool(has_power_outlet),
            "seatType": seat_type,
            "limit": limit,
        }.items() if v is not None}
        return self._send("GET", "/api/v1/ai-tools/optimal-seats", params=params)

    def occupancy(self, zone_code: str | None = None) -> list[dict]:
        params = {"zoneCode": zone_code} if zone_code else None
        return self._send("GET", "/api/v1/ai-tools/occupancy", params=params)

    def reserve(self, seat_code: str) -> dict:
        return self._send("POST", "/api/v1/ai-tools/reservations", json={"seatCode": seat_code})

    def active_reservation(self) -> dict | None:
        return self._send("GET", "/api/v1/reservations/me/active")


def _bool(v: bool | None) -> str | None:
    return None if v is None else ("true" if v else "false")
