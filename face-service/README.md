# face-service

Face check before every seat booking: **YOLOv8n-face** finds the face, **InsightFace 2d106det** gives landmarks,
**ArcFace (MobileFaceNet, w600k_mbf)** turns the aligned face into a 512-d identity embedding, and a head-turn
challenge checks liveness. CPU only (ONNX Runtime, no PyTorch), ~130 ms per frame.

The service is **stateless and internal**: only backend-core calls it (header `X-Internal-Key`), it never stores
images or embeddings. backend-core keeps the enrolled template and decides (cosine ≥ 0.45). See
[docs/architecture.md](../docs/architecture.md#face-verification-before-booking).

## API

`POST /v1/analyze`

```json
{"steps": ["CENTER", "RIGHT", "LEFT"],
 "frames": [{"step": "CENTER", "image": "<base64 jpeg>"}, {"step": "RIGHT", "image": "..."}]}
```

```json
{"status": "OK", "reason": null, "embedding": [0.012, ...512], "consistency": 0.71,
 "frames": [{"step": "CENTER", "faces": 1, "detScore": 0.87, "yaw": -0.04, "pose": "CENTER"}],
 "model": "yolov8n-face+2d106det+w600k_mbf", "latencyMs": 1130}
```

`status`: `OK` · `NO_FACE` (no frontal face) · `MULTIPLE_FACES` (two similar-size faces at the camera; small faces
in the background are ignored) · `LIVENESS_FAILED` (a step was not performed, or the face changed mid-challenge).
`400 INVALID_IMAGE`, `413 TOO_MANY_FRAMES`, `401` on a wrong key. `GET /health`.

## Pipeline

| Stage | Model / method |
|---|---|
| Detection | YOLOv8n-face (lindevs, WIDER FACE), 640 letterbox, conf 0.5, NMS 0.45 |
| Landmarks | 2d106det on a 1.5× crop → 5 points (eyes, nose tip, mouth corners: indices 38, 88, 86, 52, 61) |
| Liveness | yaw = (nose.x − eye midpoint.x) / eye distance on the raw frame; CENTER if \|yaw\| ≤ 0.12, LEFT ≥ 0.25, RIGHT ≤ −0.25; steps must appear in challenge order. A flat photo has no parallax, so its yaw stays ≈ 0 |
| Alignment | similarity transform to the ArcFace 112×112 template |
| Embedding | w600k_mbf, L2-normalised; template = mean of the frontal frames; every frame must be ≥ 0.30 cosine to it |

Measured on the test fixture and an InsightFace sample group photo (6 people): same person (mirrored / darkened)
0.92–0.97, different people max 0.21, mean 0.05.

## Run

```sh
python -m venv .venv && .venv/Scripts/pip install -e ".[dev]"   # Windows; bin/pip on Linux
python scripts/download_models.py models                          # ~140 MB download, SHA-256 checked
pytest -q                                                         # model tests are skipped without models/
uvicorn app.main:app --port 8001
```

Docker bakes the models into the image (`docker compose --profile app up` builds it). Settings (env): `INTERNAL_KEY`,
`ONNX_THREADS`, `DET_CONF`, `MIN_FACE_PX`, `YAW_CENTER_MAX`, `YAW_TURN_MIN`, `CONSISTENCY_MIN`, `MAX_FRAMES`.

## Licences

Models are downloaded, not committed: YOLOv8-face weights (lindevs/yolov8-face, AGPL-3.0, Ultralytics) and InsightFace
buffalo_s models (non-commercial research use only). Fine for this academic project; a commercial deployment would need
other weights. Test image: NASA portrait of Eileen Collins (public domain, from scikit-image).
