"""Which seats to simulate."""

from __future__ import annotations

import logging
from dataclasses import dataclass

import httpx

from simulator.config import Settings

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class SeatSpec:
    seat_code: str
    zone_code: str
    device_id: str
    quiet_zone: bool = False
    group_table: bool = False

    @property
    def topic_base(self) -> str:
        return f"library/zone/{self.zone_code}/seat/{self.seat_code}"


def default_seats() -> list[SeatSpec]:
    """Mirror of V2__seed_data.sql: zones A (quiet), B, C (C-01..C-08 group tables), 20 seats each."""
    seats = []
    for zone in ("A", "B", "C"):
        for n in range(1, 21):
            code = f"{zone}-{n:02d}"
            seats.append(SeatSpec(
                seat_code=code,
                zone_code=zone,
                device_id="SIM-" + code.replace("-", ""),
                quiet_zone=zone == "A",
                group_table=zone == "C" and n <= 8,
            ))
    return seats


def fetch_seats(backend_url: str, timeout: float = 3.0) -> list[SeatSpec]:
    """Seats whose device is simulated, from GET /api/v1/seats (public endpoint)."""
    resp = httpx.get(f"{backend_url.rstrip('/')}/api/v1/seats", timeout=timeout)
    resp.raise_for_status()
    seats = []
    for s in resp.json():
        if not s.get("simulatedDevice", True):
            continue
        seats.append(SeatSpec(
            seat_code=s["seatCode"],
            zone_code=s["zoneCode"],
            device_id=s.get("deviceId") or "SIM-" + s["seatCode"].replace("-", ""),
            quiet_zone=bool(s.get("quietZone")),
            group_table=s.get("seatType") == "GROUP_TABLE",
        ))
    return seats


def load_seats(settings: Settings) -> list[SeatSpec]:
    seats: list[SeatSpec] | None = None
    if settings.backend_url:
        try:
            seats = fetch_seats(settings.backend_url)
            log.info("Loaded %d simulated seats from %s", len(seats), settings.backend_url)
        except Exception as exc:  # backend down or not started yet
            log.warning("Could not load seats from backend (%s); using built-in layout", exc)
    if not seats:
        seats = default_seats()
    excluded = settings.excluded()
    seats = [s for s in seats if s.seat_code.upper() not in excluded]
    return seats[: max(0, settings.sim_seats)]
