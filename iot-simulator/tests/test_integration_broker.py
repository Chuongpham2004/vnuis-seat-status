"""Against a real MQTT broker. Skipped unless SIM_IT_MQTT_HOST is set, e.g.

    SIM_IT_MQTT_HOST=localhost SIM_IT_MQTT_USER=simulator SIM_IT_MQTT_PASSWORD=simulator_dev_pw \
    SIM_IT_SUB_USER=monitor SIM_IT_SUB_PASSWORD=monitor_dev_pw pytest -m integration
"""

from __future__ import annotations

import json
import os
import queue
import time
import uuid

import paho.mqtt.client as mqtt
import pytest

from simulator.config import Settings
from simulator.seats import SeatSpec
from simulator.virtual_seat import VirtualSeat

HOST = os.getenv("SIM_IT_MQTT_HOST")
pytestmark = [pytest.mark.integration, pytest.mark.skipif(not HOST, reason="SIM_IT_MQTT_HOST not set")]


def _subscriber(topic: str) -> tuple[mqtt.Client, "queue.Queue[tuple[str, dict]]"]:
    q: "queue.Queue[tuple[str, dict]]" = queue.Queue()
    c = mqtt.Client(callback_api_version=mqtt.CallbackAPIVersion.VERSION2, client_id=f"it-sub-{uuid.uuid4().hex[:6]}")
    if os.getenv("SIM_IT_SUB_USER"):
        c.username_pw_set(os.getenv("SIM_IT_SUB_USER"), os.getenv("SIM_IT_SUB_PASSWORD"))
    c.on_message = lambda _c, _u, msg: q.put((msg.topic, json.loads(msg.payload) if msg.payload else {}))
    c.on_connect = lambda cl, *_a: cl.subscribe(topic, qos=1)
    c.connect(HOST, int(os.getenv("SIM_IT_MQTT_PORT", "1883")))
    c.loop_start()
    time.sleep(1.0)
    return c, q


def _wait_for(q, predicate, timeout=30.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            topic, payload = q.get(timeout=0.5)
        except queue.Empty:
            continue
        if predicate(topic, payload):
            return payload
    raise AssertionError("expected MQTT message did not arrive")


def test_sit_and_last_will_against_real_broker():
    code = "C-20"
    spec = SeatSpec(seat_code=code, zone_code="C", device_id="SIM-C20")
    settings = Settings(backend_url=None, mqtt_host=HOST, mqtt_port=int(os.getenv("SIM_IT_MQTT_PORT", "1883")),
                        mqtt_user=os.getenv("SIM_IT_MQTT_USER", ""), mqtt_password=os.getenv("SIM_IT_MQTT_PASSWORD", ""),
                        mqtt_keepalive=5, _env_file=None)
    sub, q = _subscriber(f"library/zone/C/seat/{code}/#")
    seat = VirtualSeat(spec, settings)
    try:
        seat.connect()
        _wait_for(q, lambda t, p: t.endswith("/status") and p.get("status") == "ONLINE")

        seat.sit()
        sat = _wait_for(q, lambda t, p: t.endswith("/telemetry") and p.get("objectDetected") is True)
        assert sat["distanceCm"] < 50

        seat.disconnect()  # no DISCONNECT packet -> broker must publish the Last Will
        _wait_for(q, lambda t, p: t.endswith("/status") and p.get("status") == "OFFLINE")
    finally:
        seat.shutdown()
        sub.loop_stop()
        sub.disconnect()
