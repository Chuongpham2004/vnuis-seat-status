"""Download the three ONNX models into MODEL_DIR (default ./models), verifying SHA-256.

    python scripts/download_models.py [model_dir]

Sources and licences (academic / non-commercial use):
  yolov8n-face.onnx  lindevs/yolov8-face 1.0.1, YOLOv8n trained on WIDER FACE (AGPL-3.0)
  2d106det.onnx      InsightFace buffalo_s (non-commercial research licence)
  w600k_mbf.onnx     InsightFace buffalo_s, ArcFace MobileFaceNet on WebFace600K (same)
"""

from __future__ import annotations

import hashlib
import io
import os
import sys
import urllib.request
import zipfile

YOLO_URL = "https://github.com/lindevs/yolov8-face/releases/download/1.0.1/yolov8n-face-lindevs.onnx"
BUFFALO_URL = "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_s.zip"

SHA256 = {
    "yolov8n-face.onnx": "8d0bfb0c3383c5bd7a78dd24ef79a21e2aa456619b6ab5e53867092d1c7dc414",
    "2d106det.onnx": "f001b856447c413801ef5c42091ed0cd516fcd21f2d6b79635b1e733a7109dbf",
    "w600k_mbf.onnx": "9cc6e4a75f0e2bf0b1aed94578f144d15175f357bdc05e815e5c4a02b319eb4f",
}


def fetch(url: str) -> bytes:
    print(f"downloading {url}", flush=True)
    with urllib.request.urlopen(url, timeout=300) as r:  # noqa: S310 - fixed https URLs
        return r.read()


def save(model_dir: str, name: str, data: bytes) -> None:
    digest = hashlib.sha256(data).hexdigest()
    if digest != SHA256[name]:
        raise SystemExit(f"{name}: sha256 {digest} != expected {SHA256[name]}")
    with open(os.path.join(model_dir, name), "wb") as f:
        f.write(data)
    print(f"  {name} ok ({len(data) // 1024} KiB)")


def present(model_dir: str, name: str) -> bool:
    path = os.path.join(model_dir, name)
    if not os.path.exists(path):
        return False
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest() == SHA256[name]


def main() -> None:
    model_dir = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("MODEL_DIR", "models")
    os.makedirs(model_dir, exist_ok=True)
    if not present(model_dir, "yolov8n-face.onnx"):
        save(model_dir, "yolov8n-face.onnx", fetch(YOLO_URL))
    wanted = ["2d106det.onnx", "w600k_mbf.onnx"]
    if not all(present(model_dir, n) for n in wanted):
        with zipfile.ZipFile(io.BytesIO(fetch(BUFFALO_URL))) as z:  # ~120 MB, only two members kept
            for n in wanted:
                member = next(m for m in z.namelist() if m.endswith(n))
                save(model_dir, n, z.read(member))
    print(f"models ready in {model_dir}")


if __name__ == "__main__":
    main()
