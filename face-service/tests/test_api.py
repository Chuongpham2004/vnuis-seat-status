from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


class StubPipeline:
    def __init__(self, settings: Settings):
        self.s = settings
        self.calls = []

    def analyze(self, steps, frames):
        self.calls.append((steps, [(step, img.shape) for step, img in frames]))
        return {"status": "OK", "reason": None, "embedding": [0.0] * 512, "consistency": 1.0, "frames": [],
                "model": "stub", "latencyMs": 1}


def client(key: str = "") -> tuple[TestClient, StubPipeline]:
    stub = StubPipeline(Settings(internal_key=key, max_frames=4, max_frame_bytes=50_000))
    return TestClient(create_app(pipeline=stub)), stub


def body(img: str, steps=("CENTER",), frame_steps=("CENTER",)):
    return {"steps": list(steps), "frames": [{"step": s, "image": img} for s in frame_steps]}


def test_health():
    c, _ = client()
    with c:
        r = c.get("/health")
    assert r.status_code == 200 and r.json()["modelsLoaded"] is True


def test_analyze_passes_decoded_frames(blank_b64):
    c, stub = client()
    r = c.post("/v1/analyze", json=body(blank_b64, ("CENTER", "LEFT"), ("CENTER", "LEFT")))
    assert r.status_code == 200 and r.json()["status"] == "OK"
    assert stub.calls == [(["CENTER", "LEFT"], [("CENTER", (120, 160, 3)), ("LEFT", (120, 160, 3))])]


def test_accepts_data_url(blank_b64):
    c, _ = client()
    assert c.post("/v1/analyze", json=body("data:image/jpeg;base64," + blank_b64)).status_code == 200


def test_internal_key_required(blank_b64):
    c, _ = client(key="s3cret")
    assert c.post("/v1/analyze", json=body(blank_b64)).status_code == 401
    r = c.post("/v1/analyze", json=body(blank_b64), headers={"X-Internal-Key": "wrong"})
    assert r.status_code == 401 and r.json()["code"] == "UNAUTHENTICATED"
    assert c.post("/v1/analyze", json=body(blank_b64), headers={"X-Internal-Key": "s3cret"}).status_code == 200


def test_rejects_garbage_image():
    c, _ = client()
    r = c.post("/v1/analyze", json=body("bm90IGFuIGltYWdlIGF0IGFsbA=="))
    assert r.status_code == 400 and r.json()["code"] == "INVALID_IMAGE"


def test_rejects_unknown_step(blank_b64):
    c, _ = client()
    assert c.post("/v1/analyze", json=body(blank_b64, ("UP",), ("UP",))).status_code == 422


def test_rejects_frame_for_step_not_in_challenge(blank_b64):
    c, _ = client()
    assert c.post("/v1/analyze", json=body(blank_b64, ("CENTER",), ("LEFT",))).status_code == 422


def test_too_many_frames(blank_b64):
    c, _ = client()
    r = c.post("/v1/analyze", json=body(blank_b64, ("CENTER",), ("CENTER",) * 5))
    assert r.status_code == 413
