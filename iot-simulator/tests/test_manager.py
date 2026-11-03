import random

import pytest

from simulator.commands import ManagerBackend, execute
from simulator.manager import SimulatorManager, UnknownSeat
from simulator.seats import default_seats


def test_manages_59_seats_excluding_the_physical_a01(manager):
    assert len(manager.seats) == 59
    assert "A-01" not in manager.seats
    assert {"A-02", "A-20", "B-01", "C-20"} <= set(manager.seats)


def test_heartbeats_every_interval(manager, factory, clock):
    before = len(factory.for_seat("B-03").published)
    clock.advance(31)
    manager.tick()
    assert len(factory.for_seat("B-03").published) == before + 1


def test_manual_commands_pin_the_seat_and_ambient_leaves_it_alone(manager, factory, clock, settings):
    manager.sit("a-02")  # case-insensitive
    seat = manager.seat("A-02")
    assert seat.present and seat.pinned
    settings.speed = 3600.0  # one simulated hour per real second: ambient is very active
    for _ in range(50):
        clock.advance(1)
        manager.tick()
    assert seat.present  # never touched by ambient behaviour
    manager.release("A-02")
    assert not seat.pinned


def test_ambient_mode_moves_students_and_manual_mode_freezes(settings, factory, clock):
    settings.speed = 600.0  # 10 simulated minutes per real second
    settings.start_hour = 10.0  # a busy morning
    manager = SimulatorManager(settings, [s for s in default_seats() if s.seat_code != "A-01"],
                               client_factory=factory, clock=clock, rng=random.Random(3))
    for seat in manager.seats.values():
        seat.connect()
    manager.tick()
    actions = []
    for _ in range(60):
        clock.advance(1)
        actions += manager.tick()
    assert actions, "ambient behaviour should seat some students"

    manager.set_mode("manual")
    frozen = []
    for _ in range(60):
        clock.advance(1)
        frozen += manager.tick()
    assert frozen == []


def test_disconnect_and_reconnect(manager, factory):
    manager.disconnect("C-05")
    first = factory.for_seat("C-05")
    assert first.sock.closed and not manager.seat("C-05").connected
    manager.reconnect("C-05")
    assert manager.seat("C-05").connected
    assert factory.for_seat("C-05") is not first


def test_scenarios(manager):
    result = manager.scenario("rush_hour")
    present = sum(s.present for s in manager.seats.values())
    assert result["seatsChanged"] == present > 20
    manager.scenario("mass_leave")
    assert not any(s.present for s in manager.seats.values())
    manager.sit("B-01")
    manager.scenario("reset")
    assert not manager.seat("B-01").pinned and not manager.seat("B-01").present
    with pytest.raises(ValueError):
        manager.scenario("nope")


def test_unknown_seat(manager):
    with pytest.raises(UnknownSeat):
        manager.sit("A-01")  # the physical seat is not simulated
    with pytest.raises(UnknownSeat):
        manager.sit("Z-99")


def test_text_commands_used_by_the_interactive_console(manager):
    backend = ManagerBackend(manager)
    assert '"present": true' in execute("sit A-02", backend)
    assert '"present": false' in execute("stand A-02", backend)
    assert '"connected": false' in execute("disconnect A-02", backend)
    assert '"connected": true' in execute("reconnect A-02", backend)
    assert '"mode": "manual"' in execute("mode manual", backend)
    assert "not simulated" in execute("sit A-01", backend)
    assert "usage" in execute("sit", backend)
    assert "commands:" in execute("help", backend)
    with pytest.raises(SystemExit):
        execute("quit", backend)


def test_default_layout_matches_seed_data():
    seats = default_seats()
    assert len(seats) == 60
    c01 = next(s for s in seats if s.seat_code == "C-01")
    assert c01.group_table and c01.topic_base == "library/zone/C/seat/C-01"
