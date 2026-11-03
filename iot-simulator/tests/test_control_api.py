from fastapi.testclient import TestClient

from simulator.control_api import create_app


def test_http_control_api(manager):
    client = TestClient(create_app(manager))

    assert client.get("/health").json() == {"status": "ok", "seats": 59, "connected": 59}
    assert len(client.get("/seats").json()) == 59

    sat = client.post("/seats/A-02/sit").json()
    assert sat["present"] is True and sat["pinned"] is True

    assert client.post("/seats/A-02/stand").json()["present"] is False
    assert client.post("/seats/A-02/disconnect").json()["connected"] is False
    assert client.post("/seats/A-02/reconnect").json()["connected"] is True
    assert client.post("/seats/A-02/release").json()["pinned"] is False

    assert client.post("/seats/A-01/sit").status_code == 404
    assert client.post("/mode", json={"mode": "manual"}).json() == {"mode": "manual"}
    assert client.post("/mode", json={"mode": "chaos"}).status_code == 422
    assert client.post("/scenario/mass_leave").status_code == 200
    assert client.post("/scenario/unknown").status_code == 400

    status = client.get("/status").json()
    assert status["mode"] == "manual" and status["seats"] == 59
