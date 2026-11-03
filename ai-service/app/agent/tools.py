"""The assistant's three tools: one JSON-schema definition, one executor, used by every engine."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Any, Callable

from app.agent.backend import BackendClient, BackendError
from app.forecast.service import ForecastService, UnknownZone

TOOL_SPECS: list[dict[str, Any]] = [
    {
        "name": "find_optimal_seats",
        "description": "Find AVAILABLE library seats ranked by fit (requested features, emptier zones first). "
                       "Use before booking when the student describes what they want instead of naming a seat.",
        "parameters": {
            "type": "object",
            "properties": {
                "zone_code": {"type": "string", "enum": ["A", "B", "C"],
                              "description": "Preferred zone. A = quiet zone, B = general, C = group tables."},
                "is_quiet_zone": {"type": "boolean", "description": "Only quiet-zone seats."},
                "has_power_outlet": {"type": "boolean", "description": "Only seats with a power outlet."},
                "seat_type": {"type": "string", "enum": ["SINGLE", "GROUP_TABLE"]},
                "limit": {"type": "integer", "minimum": 1, "maximum": 10, "default": 3},
            },
        },
    },
    {
        "name": "get_occupancy_forecast",
        "description": "Predicted occupancy (0-100 %) of a zone at a given hour (today, or tomorrow if that hour "
                       "has passed), plus the live occupancy now. Omit zone_code to compare all zones.",
        "parameters": {
            "type": "object",
            "properties": {
                "zone_code": {"type": "string", "enum": ["A", "B", "C"]},
                "target_hour": {"type": "integer", "minimum": 0, "maximum": 23,
                                "description": "Local hour 0-23 (Asia/Ho_Chi_Minh). Omit for the next hour."},
                "day": {"type": "string", "enum": ["today", "tomorrow"], "default": "today"},
            },
        },
    },
    {
        "name": "reserve_seat",
        "description": "Reserve a seat for the signed-in student (held 15 minutes; the student must then sit down "
                       "and scan the table QR code to check in). Only call when the student asked to book.",
        "parameters": {
            "type": "object",
            "properties": {"seat_code": {"type": "string", "description": "e.g. A-05"}},
            "required": ["seat_code"],
        },
    },
]


@dataclass
class ToolCall:
    name: str
    arguments: dict[str, Any]
    ok: bool
    result: Any = None
    error: dict[str, Any] | None = None

    def as_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {"name": self.name, "arguments": self.arguments, "ok": self.ok}
        if self.ok:
            out["result"] = self.result
        else:
            out["error"] = self.error
        return out


@dataclass
class ToolExecutor:
    backend: BackendClient
    forecasts: ForecastService
    now: Callable[[], datetime]
    trace: list[ToolCall] = field(default_factory=list)

    def call(self, name: str, arguments: dict[str, Any] | None) -> ToolCall:
        args = {k: v for k, v in (arguments or {}).items() if v is not None}
        fn = {"find_optimal_seats": self.find_optimal_seats,
              "get_occupancy_forecast": self.get_occupancy_forecast,
              "reserve_seat": self.reserve_seat}.get(name)
        if fn is None:
            call = ToolCall(name, args, False, error={"code": "UNKNOWN_TOOL", "detail": name})
        else:
            try:
                call = ToolCall(name, args, True, result=fn(**args))
            except BackendError as exc:
                call = ToolCall(name, args, False, error=exc.as_dict())
            except (TypeError, ValueError, UnknownZone) as exc:
                call = ToolCall(name, args, False, error={"code": "INVALID_ARGUMENTS", "detail": str(exc)})
        self.trace.append(call)
        return call

    # ------------------------------------------------------------------ tools

    def find_optimal_seats(self, zone_code: str | None = None, is_quiet_zone: bool | None = None,
                           has_power_outlet: bool | None = None, seat_type: str | None = None,
                           limit: int = 3) -> list[dict[str, Any]]:
        recs = self.backend.optimal_seats(zone_code.upper() if zone_code else None, is_quiet_zone, has_power_outlet,
                                          seat_type, int(limit))
        # Keep only what the model (and the student) needs.
        return [{
            "seatCode": r["seat"]["seatCode"],
            "zoneCode": r["seat"]["zoneCode"],
            "seatType": r["seat"]["seatType"],
            "hasPowerOutlet": r["seat"]["hasPowerOutlet"],
            "quietZone": r["seat"]["quietZone"],
            "zoneOccupancyPercent": int(round(r.get("zoneOccupancyRate", 0) * 100)),
            "score": r.get("score"),
            "reasons": r.get("reasons", []),
        } for r in recs]

    def get_occupancy_forecast(self, zone_code: str | None = None, target_hour: int | None = None,
                               day: str = "today") -> dict[str, Any]:
        now = self.now()
        if target_hour is None:
            target = (now + timedelta(hours=1)).replace(minute=0, second=0, microsecond=0)
        else:
            hour = int(target_hour)
            if not 0 <= hour <= 23:
                raise ValueError("target_hour must be 0-23")
            target = now.replace(hour=hour, minute=0, second=0, microsecond=0)
            if day == "tomorrow" or target < now.replace(minute=0, second=0, microsecond=0):
                target += timedelta(days=1)
        zones = [zone_code.upper()] if zone_code else self.forecasts.zone_codes()
        live: dict[str, dict] = {}
        try:
            for item in self.backend.occupancy(zone_code.upper() if zone_code else None):
                z = item["zone"]
                live[z["code"]] = {"occupied": z["occupied"] + z["away"], "reserved": z["reserved"],
                                   "available": z["available"], "total": z["totalSeats"],
                                   "occupancyPercent": int(round(z["occupancyRate"] * 100))}
        except BackendError:
            pass  # the forecast alone is still useful
        results = []
        for z in zones:
            p = self.forecasts.at(z, target, now=now)
            results.append({"zoneCode": z, "targetTime": p["time"], "predictedPercent": p["predictedPercent"],
                            "level": p["level"], "lowerPercent": int(round(p["lower"] * 100)),
                            "upperPercent": int(round(p["upper"] * 100)), "model": p["model"],
                            "liveNow": live.get(z)})
        return {"target": target.isoformat(), "zones": results}

    def reserve_seat(self, seat_code: str) -> dict[str, Any]:
        r = self.backend.reserve(seat_code.strip().upper())
        return {"reservationId": r["id"], "seatCode": r["seatCode"], "zoneCode": r["zoneCode"],
                "status": r["status"], "expireAt": r.get("expireAt"),
                "secondsUntilExpiry": r.get("secondsUntilExpiry")}
