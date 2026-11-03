"""One virtual seat node = one MQTT client, publishing exactly what the ESP32 firmware publishes."""

from __future__ import annotations

import json
import logging
import random
import threading
import time
import uuid
from typing import Any, Callable, Protocol

import paho.mqtt.client as mqtt

from simulator.config import Settings
from simulator.seats import SeatSpec

log = logging.getLogger(__name__)

THRESHOLD_CM = 50.0


class MqttClientLike(Protocol):
    """The subset of paho.mqtt.client.Client used here (lets tests inject a fake)."""

    on_connect: Any
    on_disconnect: Any

    def username_pw_set(self, username: str, password: str | None = None) -> None: ...
    def will_set(self, topic: str, payload: str, qos: int = 0, retain: bool = False) -> None: ...
    def reconnect_delay_set(self, min_delay: int = 1, max_delay: int = 120) -> None: ...
    def connect_async(self, host: str, port: int = 1883, keepalive: int = 60) -> None: ...
    def loop_start(self) -> Any: ...
    def loop_stop(self) -> Any: ...
    def publish(self, topic: str, payload: str, qos: int = 0, retain: bool = False) -> Any: ...
    def disconnect(self) -> Any: ...
    def socket(self) -> Any: ...


ClientFactory = Callable[[str], MqttClientLike]


def paho_client_factory(client_id: str) -> MqttClientLike:
    return mqtt.Client(callback_api_version=mqtt.CallbackAPIVersion.VERSION2, client_id=client_id,
                       clean_session=True)


def _failed(reason_code: Any) -> bool:
    # paho 2 passes a ReasonCode object; fakes may pass a plain int.
    if hasattr(reason_code, "is_failure"):
        return bool(reason_code.is_failure)
    return reason_code != 0


class VirtualSeat:
    def __init__(self, spec: SeatSpec, settings: Settings, client_factory: ClientFactory = paho_client_factory,
                 rng: random.Random | None = None, clock: Callable[[], float] = time.time):
        self.spec = spec
        self.settings = settings
        self._factory = client_factory
        self._rng = rng or random.Random()
        self._clock = clock
        self._lock = threading.RLock()
        self._client: MqttClientLike | None = None

        self.present = False
        self.connected = False
        self.online_wanted = False  # False after disconnect(): no auto-reconnect
        self.pinned = False  # manually controlled; ambient behaviour leaves it alone
        self.last_distance_cm: float | None = None
        self.last_publish_at: float | None = None
        self.published = 0

    # ------------------------------------------------------------------ topics / payloads

    @property
    def telemetry_topic(self) -> str:
        return self.spec.topic_base + "/telemetry"

    @property
    def status_topic(self) -> str:
        return self.spec.topic_base + "/status"

    def _status_payload(self, status: str) -> str:
        payload: dict[str, Any] = {"deviceId": self.spec.device_id, "status": status}
        if status == "ONLINE":
            payload["ts"] = int(self._clock() * 1000)
        return json.dumps(payload, separators=(",", ":"))

    def _distance(self) -> float:
        # Realistic HC-SR04 readings: a seated student is 20-45 cm away, an empty chair 90-220 cm.
        d = self._rng.uniform(20.0, 45.0) if self.present else self._rng.uniform(90.0, 220.0)
        return round(d, 1)

    def telemetry_payload(self) -> dict[str, Any]:
        self.last_distance_cm = self._distance()
        return {
            "deviceId": self.spec.device_id,
            "seatCode": self.spec.seat_code,
            "distanceCm": self.last_distance_cm,
            "objectDetected": self.present,  # already "debounced", like the firmware output
            "rssi": self._rng.randint(-75, -45),
            "simulated": True,
            "fw": "sim-0.3.0",
            "ts": int(self._clock() * 1000),
        }

    # ------------------------------------------------------------------ connection

    def connect(self) -> None:
        with self._lock:
            if self._client is not None:
                return
            client = self._factory(f"sim-{self.spec.device_id}-{uuid.uuid4().hex[:6]}")
            if self.settings.mqtt_user:
                client.username_pw_set(self.settings.mqtt_user, self.settings.mqtt_password)
            # Last Will: retained OFFLINE, published by the broker if this client vanishes.
            client.will_set(self.status_topic, self._status_payload("OFFLINE"), qos=1, retain=True)
            client.on_connect = self._on_connect
            client.on_disconnect = self._on_disconnect
            client.reconnect_delay_set(min_delay=1, max_delay=30)
            client.connect_async(self.settings.mqtt_host, self.settings.mqtt_port, keepalive=self.settings.mqtt_keepalive)
            # Store the client before starting the loop: on_connect may fire immediately.
            self._client = client
            self.online_wanted = True
            client.loop_start()

    def _on_connect(self, client, userdata, flags, reason_code, properties=None) -> None:
        if _failed(reason_code):
            log.warning("%s: MQTT connect refused (%s)", self.spec.seat_code, reason_code)
            return
        with self._lock:
            self.connected = True
            client.publish(self.status_topic, self._status_payload("ONLINE"), qos=1, retain=True)
        self.publish_telemetry()

    def _on_disconnect(self, client, userdata, flags=None, reason_code=None, properties=None) -> None:
        with self._lock:
            self.connected = False

    def disconnect(self) -> None:
        """Simulate a crash / power loss: drop the TCP connection WITHOUT an MQTT DISCONNECT,
        so the broker publishes the Last Will (retained OFFLINE)."""
        with self._lock:
            client, self._client = self._client, None
            self.online_wanted = False
            self.connected = False
        if client is None:
            return
        client.loop_stop()
        sock = client.socket()
        if sock is not None:
            try:
                sock.close()
            except OSError:
                pass

    def reconnect(self) -> None:
        self.connect()

    def shutdown(self) -> None:
        """Clean stop: publish OFFLINE ourselves, then a normal DISCONNECT (no Last Will)."""
        with self._lock:
            client, self._client = self._client, None
            self.online_wanted = False
            was_connected, self.connected = self.connected, False
        if client is None:
            return
        if was_connected:
            info = client.publish(self.status_topic, self._status_payload("OFFLINE"), qos=1, retain=True)
            if hasattr(info, "wait_for_publish"):
                try:
                    info.wait_for_publish(timeout=2)
                except Exception:  # noqa: BLE001 - best effort on shutdown
                    pass
        client.disconnect()
        client.loop_stop()

    # ------------------------------------------------------------------ sensor

    def sit(self) -> None:
        self.present = True
        self.publish_telemetry()

    def stand(self) -> None:
        self.present = False
        self.publish_telemetry()

    def publish_telemetry(self) -> bool:
        with self._lock:
            if self._client is None or not self.connected:
                return False
            payload = json.dumps(self.telemetry_payload(), separators=(",", ":"))
            self._client.publish(self.telemetry_topic, payload, qos=1, retain=False)
            self.last_publish_at = self._clock()
            self.published += 1
            return True

    def heartbeat_due(self, now: float, interval: float) -> bool:
        return self.connected and (self.last_publish_at is None or now - self.last_publish_at >= interval)

    def snapshot(self) -> dict[str, Any]:
        return {
            "seatCode": self.spec.seat_code,
            "zoneCode": self.spec.zone_code,
            "deviceId": self.spec.device_id,
            "present": self.present,
            "connected": self.connected,
            "pinned": self.pinned,
            "lastDistanceCm": self.last_distance_cm,
            "published": self.published,
        }
