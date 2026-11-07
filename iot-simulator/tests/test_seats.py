from simulator.config import Settings
from simulator.seats import load_seats


def test_skips_physical_a01_by_default():
    seats = load_seats(Settings(backend_url=None))
    assert len(seats) == 59
    assert "A-01" not in {s.seat_code for s in seats}


def test_simulate_esp32_also_plays_a01():
    seats = {s.seat_code: s for s in load_seats(Settings(backend_url=None, simulate_esp32=True))}
    assert len(seats) == 60
    assert seats["A-01"].device_id == "ESP32-A01"
