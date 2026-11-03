from __future__ import annotations

import json
import random
from dataclasses import dataclass, field
from typing import Any

import pytest

from simulator.config import Settings
from simulator.manager import SimulatorManager
from simulator.seats import default_seats


class FakeSocket:
    def __init__(self) -> None:
        self.closed = False

    def close(self) -> None:
        self.closed = True


@dataclass
class Published:
    topic: str
    payload: str
    qos: int
    retain: bool

    def json(self) -> dict[str, Any]:
        return json.loads(self.payload)


@dataclass
class FakeClient:
    """Stands in for paho.mqtt.client.Client; connects instantly when loop_start() is called."""

    client_id: str
    published: list[Published] = field(default_factory=list)
    will: Published | None = None
    credentials: tuple[str, str | None] | None = None
    connected_to: tuple[str, int, int] | None = None
    loop_running: bool = False
    clean_disconnect: bool = False
    sock: FakeSocket = field(default_factory=FakeSocket)
    on_connect: Any = None
    on_disconnect: Any = None
    auto_connect: bool = True

    def username_pw_set(self, username, password=None):
        self.credentials = (username, password)

    def will_set(self, topic, payload, qos=0, retain=False):
        self.will = Published(topic, payload, qos, retain)

    def reconnect_delay_set(self, min_delay=1, max_delay=120):
        pass

    def connect_async(self, host, port=1883, keepalive=60):
        self.connected_to = (host, port, keepalive)

    def loop_start(self):
        self.loop_running = True
        if self.auto_connect and self.on_connect:
            self.on_connect(self, None, {}, 0, None)

    def loop_stop(self):
        self.loop_running = False

    def publish(self, topic, payload, qos=0, retain=False):
        self.published.append(Published(topic, payload, qos, retain))

    def disconnect(self):
        self.clean_disconnect = True

    def socket(self):
        return self.sock


class FakeFactory:
    def __init__(self) -> None:
        self.clients: list[FakeClient] = []

    def __call__(self, client_id: str) -> FakeClient:
        c = FakeClient(client_id)
        self.clients.append(c)
        return c

    def for_seat(self, seat_code: str) -> FakeClient:
        """Latest client created for a seat."""
        tag = "SIM-" + seat_code.replace("-", "")
        return [c for c in self.clients if tag in c.client_id][-1]


@pytest.fixture
def settings() -> Settings:
    return Settings(backend_url=None, mqtt_host="broker.test", mqtt_port=1883, seed=42, speed=1.0,
                    heartbeat_seconds=30.0, _env_file=None)


@pytest.fixture
def factory() -> FakeFactory:
    return FakeFactory()


class FakeClock:
    def __init__(self, start: float = 1_000_000.0) -> None:
        self.now = start

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


@pytest.fixture
def clock() -> FakeClock:
    return FakeClock()


@pytest.fixture
def manager(settings, factory, clock) -> SimulatorManager:
    seats = [s for s in default_seats() if s.seat_code != "A-01"]
    m = SimulatorManager(settings, seats, client_factory=factory, clock=clock, rng=random.Random(7))
    for seat in m.seats.values():
        seat.connect()
    return m
