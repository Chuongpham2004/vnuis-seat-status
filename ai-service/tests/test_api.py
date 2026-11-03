from fastapi.testclient import TestClient

from app.main import create_app

from conftest import make_token


def client_for(services):
    return TestClient(create_app(services=services))


def test_health_and_metrics(services):
    c = client_for(services)
    assert c.get("/health").json()["modelTrained"] is True
    m = c.get("/api/v1/ai/metrics").json()
    for model in ("baseline_moving_average", "lightgbm"):
        assert set(m["models"][model]) >= {"mae", "rmse", "mape", "r2"}
    assert m["models"]["lightgbm"]["mae"] < m["models"]["baseline_moving_average"]["mae"]


def test_forecast_endpoint(services):
    c = client_for(services)
    one = c.get("/api/v1/ai/forecast", params={"zoneCode": "B", "hours": 24}).json()
    assert one["zoneCode"] == "B" and len(one["points"]) == 24
    assert c.get("/api/v1/ai/forecast", params={"zoneCode": "B", "hours": 24}).json()["cache"]["hit"] is True
    assert len(c.get("/api/v1/ai/forecast", params={"hours": 6}).json()["zones"]) == 3
    missing = c.get("/api/v1/ai/forecast", params={"zoneCode": "Z"})
    assert missing.status_code == 404 and missing.json()["code"] == "ZONE_NOT_FOUND"


def test_chat_requires_a_valid_backend_jwt(services, token):
    c = client_for(services)
    assert c.post("/api/v1/ai/chat", json={"message": "xin chào"}).status_code == 401
    bad = c.post("/api/v1/ai/chat", json={"message": "xin chào"}, headers={"Authorization": "Bearer nope"})
    assert bad.status_code == 401 and bad.json()["code"] == "UNAUTHENTICATED"
    ok = c.post("/api/v1/ai/chat", json={"message": "Đặt giúp mình ghế A-05"},
                headers={"Authorization": f"Bearer {token}"})
    assert ok.status_code == 200
    body = ok.json()
    assert body["reservation"]["seatCode"] == "A-05" and body["sessionId"]


def test_admin_endpoints_need_the_admin_role(services, token):
    c = client_for(services)
    assert c.post("/api/v1/ai/admin/train", headers={"Authorization": f"Bearer {token}"}).status_code == 403
    admin = {"Authorization": f"Bearer {make_token(role='ADMIN', sc='ADMIN001')}"}
    seeded = c.post("/api/v1/ai/admin/synthetic", json={"days": 30}, headers=admin)
    assert seeded.status_code == 200 and seeded.json()["rows"] == 30 * 24 * 3
    trained = c.post("/api/v1/ai/admin/train", headers=admin)
    assert trained.status_code == 200 and "lightgbm" in trained.json()["models"]
