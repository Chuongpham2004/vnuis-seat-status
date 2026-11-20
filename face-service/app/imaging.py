"""Decode the base64 JPEG/PNG frames the browser sends."""

from __future__ import annotations

import base64
import binascii

import cv2
import numpy as np


class InvalidImage(ValueError):
    pass


def decode_frame(data: str, max_bytes: int) -> np.ndarray:
    """Base64 (optionally a data: URL) -> BGR uint8 image."""
    if data.startswith("data:"):
        data = data.split(",", 1)[-1]
    try:
        raw = base64.b64decode(data, validate=False)
    except (binascii.Error, ValueError):
        raise InvalidImage("frame is not valid base64") from None
    if not raw:
        raise InvalidImage("empty frame")
    if len(raw) > max_bytes:
        raise InvalidImage(f"frame larger than {max_bytes} bytes")
    img = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise InvalidImage("frame is not a decodable JPEG/PNG")
    return img


def encode_frame(img: np.ndarray, quality: int = 85) -> str:
    """BGR image -> base64 JPEG (used by tests and scripts)."""
    ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, quality])
    if not ok:
        raise InvalidImage("encode failed")
    return base64.b64encode(buf.tobytes()).decode("ascii")
