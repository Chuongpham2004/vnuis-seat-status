"""Ambient student behaviour: arrival waves, study sessions and breaks.

Each virtual seat cycles EMPTY -> STUDYING <-> ON_BREAK -> EMPTY.

Arrivals follow a Poisson process whose rate is chosen so the long-run share of
seats "in a session" matches a target occupancy curve: with mean session length S
and arrival rate L, occupancy = L*S / (1 + L*S), so L = occ / ((1 - occ) * S).

Breaks make the seat physically empty for a few minutes (the backend turns that
into AWAY). Some breaks are long (> 30 min): the student never comes back and
the backend auto-releases the seat - exactly the case the platform must handle.
"""

from __future__ import annotations

import math
import random
from dataclasses import dataclass
from datetime import datetime
from enum import Enum

# Share of seats in use per local hour on a regular weekday (library open 07:00-22:00).
HOURLY_TARGET = [
    0.00, 0.00, 0.00, 0.00, 0.00, 0.00,  # 00-05
    0.02, 0.15, 0.40, 0.65, 0.80, 0.75,  # 06-11  morning wave
    0.55, 0.60, 0.78, 0.85, 0.80, 0.65,  # 12-17  lunch dip, afternoon peak
    0.50, 0.55, 0.50, 0.30, 0.08, 0.00,  # 18-23  evening, closing
]
WEEKEND_FACTOR = {5: 0.6, 6: 0.45}  # Saturday, Sunday (datetime.weekday())
# Zone personality: the quiet zone fills first, group tables peak in the afternoon.
ZONE_FACTOR = {"A": 1.10, "B": 1.0, "C": 0.85}
ZONE_AFTERNOON_BOOST = {"C": 1.25}
MAX_OCCUPANCY = 0.95


def target_occupancy(zone_code: str, when: datetime) -> float:
    """Expected share of seats in use for a zone at a local time (linear between hours)."""
    h = when.hour + when.minute / 60.0
    lo = int(h) % 24
    hi = (lo + 1) % 24
    frac = h - int(h)
    base = HOURLY_TARGET[lo] * (1 - frac) + HOURLY_TARGET[hi] * frac
    base *= WEEKEND_FACTOR.get(when.weekday(), 1.0)
    base *= ZONE_FACTOR.get(zone_code, 1.0)
    if 13 <= when.hour < 18:
        base *= ZONE_AFTERNOON_BOOST.get(zone_code, 1.0)
    return max(0.0, min(MAX_OCCUPANCY, base))


class Phase(str, Enum):
    EMPTY = "EMPTY"
    STUDYING = "STUDYING"
    ON_BREAK = "ON_BREAK"


class Action(str, Enum):
    SIT = "SIT"
    STAND = "STAND"


@dataclass
class BehaviorParams:
    mean_session_min: float = 100.0
    session_sigma: float = 0.45  # log-normal spread of session length
    mean_between_breaks_min: float = 40.0
    short_break_min: tuple[float, float] = (2.0, 12.0)
    long_break_prob: float = 0.08  # never comes back -> backend releases after 30 min AWAY
    long_break_min: tuple[float, float] = (35.0, 60.0)


@dataclass
class AmbientSeat:
    phase: Phase = Phase.EMPTY
    session_ends_at: float = 0.0  # all times: simulated epoch seconds
    next_break_at: float = 0.0
    break_ends_at: float = 0.0
    abandons: bool = False  # current break is a long one; the student is not coming back


def _session_seconds(rng: random.Random, p: BehaviorParams) -> float:
    mu = math.log(p.mean_session_min * 60) - p.session_sigma ** 2 / 2  # keeps the mean at mean_session_min
    return rng.lognormvariate(mu, p.session_sigma)


def start_session(seat: AmbientSeat, now: float, rng: random.Random, p: BehaviorParams) -> None:
    seat.phase = Phase.STUDYING
    seat.session_ends_at = now + _session_seconds(rng, p)
    seat.next_break_at = now + rng.expovariate(1 / (p.mean_between_breaks_min * 60))
    seat.abandons = False


def step(seat: AmbientSeat, now: float, dt: float, occupancy: float, rng: random.Random,
         p: BehaviorParams | None = None) -> Action | None:
    """Advance one seat by dt simulated seconds. Returns the sensor change to publish, if any."""
    p = p or BehaviorParams()
    if seat.phase is Phase.EMPTY:
        occ = min(max(occupancy, 0.0), MAX_OCCUPANCY)
        if occ <= 0:
            return None
        rate = occ / ((1 - occ) * p.mean_session_min * 60)
        if rng.random() < 1 - math.exp(-rate * dt):
            start_session(seat, now, rng, p)
            return Action.SIT
        return None

    if seat.phase is Phase.STUDYING:
        if now >= seat.session_ends_at:
            seat.phase = Phase.EMPTY
            return Action.STAND
        if now >= seat.next_break_at:
            seat.phase = Phase.ON_BREAK
            if rng.random() < p.long_break_prob:
                seat.abandons = True
                seat.break_ends_at = now + rng.uniform(*p.long_break_min) * 60
            else:
                seat.break_ends_at = now + rng.uniform(*p.short_break_min) * 60
            return Action.STAND
        return None

    # ON_BREAK
    if now >= seat.break_ends_at:
        if seat.abandons or now >= seat.session_ends_at:
            seat.phase = Phase.EMPTY  # left for good; already physically empty
            return None
        seat.phase = Phase.STUDYING
        seat.next_break_at = now + rng.expovariate(1 / (p.mean_between_breaks_min * 60))
        return Action.SIT
    return None


def in_session(seat: AmbientSeat) -> bool:
    return seat.phase is not Phase.EMPTY
