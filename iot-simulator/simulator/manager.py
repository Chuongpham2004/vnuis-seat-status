"""Runs all virtual seats: heartbeats, ambient behaviour and demo commands."""

from __future__ import annotations

import logging
import random
import threading
import time
from datetime import datetime, timedelta
from typing import Any, Callable
from zoneinfo import ZoneInfo

from simulator import behavior
from simulator.behavior import Action, AmbientSeat, BehaviorParams, Phase
from simulator.config import Settings
from simulator.seats import SeatSpec
from simulator.virtual_seat import ClientFactory, VirtualSeat, paho_client_factory

log = logging.getLogger(__name__)

LOCAL_TZ = ZoneInfo("Asia/Ho_Chi_Minh")
MODES = ("ambient", "manual")
SCENARIOS = ("rush_hour", "mass_leave", "reset")


class UnknownSeat(KeyError):
    pass


class SimulatorManager:
    def __init__(self, settings: Settings, seats: list[SeatSpec],
                 client_factory: ClientFactory = paho_client_factory,
                 clock: Callable[[], float] = time.time,
                 rng: random.Random | None = None,
                 params: BehaviorParams | None = None):
        self.settings = settings
        self._clock = clock
        self._rng = rng or random.Random(settings.seed)
        self.params = params or BehaviorParams()
        self.seats: dict[str, VirtualSeat] = {
            s.seat_code.upper(): VirtualSeat(s, settings, client_factory, random.Random(self._rng.random()), clock)
            for s in seats
        }
        self.ambient: dict[str, AmbientSeat] = {code: AmbientSeat() for code in self.seats}
        self.mode = settings.mode if settings.mode in MODES else "ambient"
        self._lock = threading.RLock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

        # Simulated clock: starts now (or at start_hour today) and runs `speed` times faster.
        self._real_start = clock()
        start = datetime.now(LOCAL_TZ)
        if settings.start_hour is not None:
            start = start.replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(hours=settings.start_hour)
        self._sim_start = start.timestamp()
        self._last_tick_sim: float | None = None

    # ------------------------------------------------------------------ clock

    def sim_now(self) -> float:
        return self._sim_start + (self._clock() - self._real_start) * self.settings.speed

    def sim_datetime(self) -> datetime:
        return datetime.fromtimestamp(self.sim_now(), LOCAL_TZ)

    # ------------------------------------------------------------------ lifecycle

    def start(self, stagger_seconds: float = 0.02) -> None:
        for seat in self.seats.values():
            seat.connect()
            if stagger_seconds:
                time.sleep(stagger_seconds)  # avoid a connect storm on the broker
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="sim-scheduler", daemon=True)
        self._thread.start()
        log.info("Simulator started: %d seats, mode=%s, speed=x%s", len(self.seats), self.mode, self.settings.speed)

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=5)
        for seat in self.seats.values():
            seat.shutdown()

    def _run(self) -> None:
        while not self._stop.wait(self.settings.tick_seconds):
            try:
                self.tick()
            except Exception:  # keep the scheduler alive no matter what
                log.exception("Scheduler tick failed")

    def tick(self) -> list[tuple[str, Action]]:
        """One scheduler step: heartbeats, then ambient behaviour. Returns the actions taken."""
        actions: list[tuple[str, Action]] = []
        now_real = self._clock()
        with self._lock:
            for seat in self.seats.values():
                if seat.heartbeat_due(now_real, self.settings.heartbeat_seconds):
                    seat.publish_telemetry()

            sim_now = self.sim_now()
            dt = 0.0 if self._last_tick_sim is None else max(0.0, sim_now - self._last_tick_sim)
            self._last_tick_sim = sim_now
            if self.mode != "ambient" or dt == 0:
                return actions
            when = datetime.fromtimestamp(sim_now, LOCAL_TZ)
            for code, seat in self.seats.items():
                if seat.pinned or not seat.connected:
                    continue
                occ = behavior.target_occupancy(seat.spec.zone_code, when)
                action = behavior.step(self.ambient[code], sim_now, dt, occ, self._rng, self.params)
                if action is Action.SIT:
                    seat.sit()
                    actions.append((code, action))
                elif action is Action.STAND:
                    seat.stand()
                    actions.append((code, action))
        return actions

    # ------------------------------------------------------------------ demo commands

    def seat(self, code: str) -> VirtualSeat:
        seat = self.seats.get(code.strip().upper())
        if seat is None:
            raise UnknownSeat(code)
        return seat

    def sit(self, code: str) -> dict[str, Any]:
        with self._lock:
            seat = self.seat(code)
            seat.pinned = True
            seat.sit()
            return seat.snapshot()

    def stand(self, code: str) -> dict[str, Any]:
        with self._lock:
            seat = self.seat(code)
            seat.pinned = True
            seat.stand()
            return seat.snapshot()

    def disconnect(self, code: str) -> dict[str, Any]:
        with self._lock:
            seat = self.seat(code)
            seat.pinned = True
            seat.disconnect()
            return seat.snapshot()

    def reconnect(self, code: str) -> dict[str, Any]:
        with self._lock:
            seat = self.seat(code)
            seat.reconnect()
            return seat.snapshot()

    def release(self, code: str) -> dict[str, Any]:
        """Hand a manually controlled seat back to the ambient behaviour."""
        with self._lock:
            seat = self.seat(code)
            seat.pinned = False
            amb = self.ambient[seat.spec.seat_code.upper()]
            if seat.present:
                behavior.start_session(amb, self.sim_now(), self._rng, self.params)
            else:
                amb.phase = Phase.EMPTY
            return seat.snapshot()

    def set_mode(self, mode: str) -> str:
        if mode not in MODES:
            raise ValueError(f"mode must be one of {MODES}")
        with self._lock:
            self.mode = mode
        return self.mode

    def scenario(self, name: str) -> dict[str, Any]:
        """Bulk demo actions on the seats that are not pinned."""
        with self._lock:
            free = [s for s in self.seats.values() if not s.pinned and s.connected]
            changed = 0
            if name == "rush_hour":
                for seat in free:
                    if not seat.present and self._rng.random() < 0.7:
                        seat.sit()
                        behavior.start_session(self.ambient[seat.spec.seat_code.upper()], self.sim_now(), self._rng, self.params)
                        changed += 1
            elif name == "mass_leave":
                for seat in free:
                    if seat.present:
                        seat.stand()
                        self.ambient[seat.spec.seat_code.upper()].phase = Phase.EMPTY
                        changed += 1
            elif name == "reset":
                for seat in self.seats.values():
                    if not seat.connected:
                        seat.reconnect()
                    seat.pinned = False
                    if seat.present:
                        seat.stand()
                        changed += 1
                    self.ambient[seat.spec.seat_code.upper()].phase = Phase.EMPTY
            else:
                raise ValueError(f"scenario must be one of {SCENARIOS}")
            return {"scenario": name, "seatsChanged": changed}

    def status(self) -> dict[str, Any]:
        with self._lock:
            seats = [s.snapshot() for s in sorted(self.seats.values(), key=lambda s: s.spec.seat_code)]
        return {
            "mode": self.mode,
            "speed": self.settings.speed,
            "simTime": self.sim_datetime().isoformat(timespec="seconds"),
            "seats": len(seats),
            "connected": sum(1 for s in seats if s["connected"]),
            "present": sum(1 for s in seats if s["present"]),
            "pinned": [s["seatCode"] for s in seats if s["pinned"]],
            "seatList": seats,
        }
