"""YOLOv8-face detector.

ONNX export of lindevs/yolov8-face; output [1, 5, 8400] = cx, cy, w, h, score in the
640x640 letterboxed input space.
"""

from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np


@dataclass(frozen=True)
class Detection:
    box: np.ndarray  # x1, y1, x2, y2 in source-image pixels
    score: float

    @property
    def short_side(self) -> float:
        return float(min(self.box[2] - self.box[0], self.box[3] - self.box[1]))


def letterbox(img: np.ndarray, size: int) -> tuple[np.ndarray, float, tuple[int, int]]:
    """Resize keeping the aspect ratio and pad to size x size (Ultralytics grey 114)."""
    h, w = img.shape[:2]
    r = min(size / h, size / w)
    nw, nh = int(round(w * r)), int(round(h * r))
    resized = cv2.resize(img, (nw, nh), interpolation=cv2.INTER_LINEAR)
    top, left = (size - nh) // 2, (size - nw) // 2
    out = np.full((size, size, 3), 114, dtype=np.uint8)
    out[top:top + nh, left:left + nw] = resized
    return out, r, (left, top)


class YoloFaceDetector:
    def __init__(self, session, conf: float = 0.5, iou: float = 0.45):
        self.session = session
        self.input_name = session.get_inputs()[0].name
        self.size = int(session.get_inputs()[0].shape[2])
        self.conf = conf
        self.iou = iou

    def detect(self, img: np.ndarray) -> list[Detection]:
        boxed, r, (left, top) = letterbox(img, self.size)
        blob = cv2.dnn.blobFromImage(boxed, 1 / 255.0, (self.size, self.size), swapRB=True)
        pred = self.session.run(None, {self.input_name: blob})[0][0].T  # (8400, 5)
        pred = pred[pred[:, 4] >= self.conf]
        if not len(pred):
            return []
        cx, cy, bw, bh, scores = pred.T
        x1 = (cx - bw / 2 - left) / r
        y1 = (cy - bh / 2 - top) / r
        xywh = np.stack([x1, y1, bw / r, bh / r], axis=1)
        keep = cv2.dnn.NMSBoxes(xywh.tolist(), scores.tolist(), self.conf, self.iou)
        h, w = img.shape[:2]
        out = []
        for i in np.array(keep).reshape(-1):
            x, y, ww, hh = xywh[i]
            box = np.array([max(0.0, x), max(0.0, y), min(w, x + ww), min(h, y + hh)], dtype=np.float32)
            out.append(Detection(box=box, score=float(scores[i])))
        return sorted(out, key=lambda d: d.score, reverse=True)
