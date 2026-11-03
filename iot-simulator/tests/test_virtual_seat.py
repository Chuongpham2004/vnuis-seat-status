from simulator.seats import SeatSpec
from simulator.virtual_seat import VirtualSeat

SPEC = SeatSpec(seat_code="B-07", zone_code="B", device_id="SIM-B07")


def make_seat(settings, factory, clock):
    seat = VirtualSeat(SPEC, settings, client_factory=factory, clock=clock)
    seat.connect()
    return seat, factory.clients[-1]


def test_connect_sets_credentials_and_retained_offline_last_will(settings, factory, clock):
    seat, client = make_seat(settings, factory, clock)
    assert client.credentials == ("simulator", "simulator_dev_pw")
    assert client.connected_to == ("broker.test", 1883, settings.mqtt_keepalive)
    assert client.will.topic == "library/zone/B/seat/B-07/status"
    assert client.will.retain is True and client.will.qos == 1
    assert client.will.json() == {"deviceId": "SIM-B07", "status": "OFFLINE"}


def test_on_connect_publishes_retained_online_then_telemetry(settings, factory, clock):
    seat, client = make_seat(settings, factory, clock)
    status, telemetry = client.published[0], client.published[1]
    assert status.topic.endswith("/status") and status.retain and status.json()["status"] == "ONLINE"
    assert telemetry.topic == "library/zone/B/seat/B-07/telemetry" and not telemetry.retain
    assert telemetry.json()["objectDetected"] is False
    assert seat.connected


def test_sit_publishes_close_distance_and_stand_far_distance(settings, factory, clock):
    seat, client = make_seat(settings, factory, clock)
    seat.sit()
    sat = client.published[-1].json()
    assert sat["objectDetected"] is True and sat["distanceCm"] < 50
    assert sat["seatCode"] == "B-07" and sat["simulated"] is True and "ts" in sat
    seat.stand()
    stood = client.published[-1].json()
    assert stood["objectDetected"] is False and stood["distanceCm"] > 50


def test_disconnect_drops_socket_without_mqtt_disconnect_so_broker_sends_lwt(settings, factory, clock):
    seat, client = make_seat(settings, factory, clock)
    seat.disconnect()
    assert client.sock.closed
    assert client.clean_disconnect is False  # a clean DISCONNECT would suppress the Last Will
    assert client.loop_running is False
    assert not seat.connected
    assert seat.publish_telemetry() is False  # nothing is sent while "dead"


def test_reconnect_creates_a_fresh_client_and_announces_online(settings, factory, clock):
    seat, _ = make_seat(settings, factory, clock)
    seat.disconnect()
    seat.reconnect()
    fresh = factory.clients[-1]
    assert len(factory.clients) == 2
    assert fresh.published[0].json()["status"] == "ONLINE"
    assert seat.connected


def test_shutdown_publishes_offline_and_disconnects_cleanly(settings, factory, clock):
    seat, client = make_seat(settings, factory, clock)
    seat.shutdown()
    assert client.published[-1].json()["status"] == "OFFLINE" and client.published[-1].retain
    assert client.clean_disconnect


def test_heartbeat_due_after_interval(settings, factory, clock):
    seat, _ = make_seat(settings, factory, clock)
    assert not seat.heartbeat_due(clock(), 30)
    clock.advance(31)
    assert seat.heartbeat_due(clock(), 30)
