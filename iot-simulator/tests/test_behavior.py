import random
from datetime import datetime
from zoneinfo import ZoneInfo

from simulator import behavior
from simulator.behavior import Action, AmbientSeat, BehaviorParams, Phase

TZ = ZoneInfo("Asia/Ho_Chi_Minh")


def test_target_occupancy_is_bounded_and_zero_at_night():
    for zone in "ABC":
        for hour in range(24):
            occ = behavior.target_occupancy(zone, datetime(2026, 11, 4, hour, 30, tzinfo=TZ))
            assert 0.0 <= occ <= behavior.MAX_OCCUPANCY
        assert behavior.target_occupancy(zone, datetime(2026, 11, 4, 3, 0, tzinfo=TZ)) == 0.0


def test_weekday_peak_is_busier_than_weekend_peak():
    wednesday = behavior.target_occupancy("B", datetime(2026, 11, 4, 15, 0, tzinfo=TZ))
    sunday = behavior.target_occupancy("B", datetime(2026, 11, 8, 15, 0, tzinfo=TZ))
    assert wednesday > sunday > 0


def test_quiet_zone_fills_faster_in_the_morning():
    morning = datetime(2026, 11, 4, 9, 0, tzinfo=TZ)
    assert behavior.target_occupancy("A", morning) > behavior.target_occupancy("C", morning)


def test_no_arrivals_when_target_is_zero():
    rng = random.Random(1)
    seat = AmbientSeat()
    for i in range(10_000):
        assert behavior.step(seat, i * 5.0, 5.0, 0.0, rng) is None
    assert seat.phase is Phase.EMPTY


def _long_run_occupancy(target: float, params: BehaviorParams, seats: int = 300, hours: float = 48) -> float:
    rng = random.Random(123)
    pool = [AmbientSeat() for _ in range(seats)]
    dt = 10.0
    samples = []
    t = 0.0
    steps = int(hours * 3600 / dt)
    for i in range(steps):
        t += dt
        for s in pool:
            behavior.step(s, t, dt, target, rng, params)
        if i > steps // 4 and i % 30 == 0:  # skip warm-up, sample every 5 simulated minutes
            samples.append(sum(behavior.in_session(s) for s in pool) / seats)
    return sum(samples) / len(samples)


def test_long_run_occupancy_matches_target_without_abandoned_seats():
    params = BehaviorParams(long_break_prob=0.0)
    occ = _long_run_occupancy(0.6, params)
    assert abs(occ - 0.6) < 0.05, occ


def test_breaks_produce_stand_then_sit_and_some_students_abandon():
    rng = random.Random(5)
    params = BehaviorParams(long_break_prob=0.5)
    actions = {Action.SIT: 0, Action.STAND: 0}
    abandoned = 0
    pool = [AmbientSeat() for _ in range(100)]
    t = 0.0
    for _ in range(int(12 * 3600 / 10)):
        t += 10
        for s in pool:
            was_break = s.phase is Phase.ON_BREAK and s.abandons
            a = behavior.step(s, t, 10, 0.7, rng, params)
            if a:
                actions[a] += 1
            if was_break and s.phase is Phase.EMPTY:
                abandoned += 1
    assert actions[Action.SIT] > 100 and actions[Action.STAND] > 100
    assert abandoned > 0  # long breaks end the session without the student sitting down again
