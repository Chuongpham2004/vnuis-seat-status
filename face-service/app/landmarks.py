"""InsightFace 2d106det: 106 facial landmarks from a face box, reduced to the 5 ArcFace points."""

from __future__ import annotations

import cv2
import numpy as np

# 106-point markup -> eye, eye, nose tip, mouth corner, mouth corner
# (image-left first, as the ArcFace template expects).
LMK106_TO_5 = [38, 88, 86, 52, 61]


class Landmark106:
    def __init__(self, session):
        self.session = session
        self.input_name = session.get_inputs()[0].name
        self.size = int(session.get_inputs()[0].shape[2])  # 192

    def predict(self, img: np.ndarray, box: np.ndarray) -> np.ndarray:
        """(106, 2) landmarks in source-image pixels."""
        w, h = box[2] - box[0], box[3] - box[1]
        cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
        scale = self.size / (max(w, h) * 1.5)
        m = np.array([[scale, 0, self.size / 2 - cx * scale],
                      [0, scale, self.size / 2 - cy * scale]], dtype=np.float32)
        crop = cv2.warpAffine(img, m, (self.size, self.size), borderValue=0)
        # The graph normalises itself (Sub/Mul nodes), so mean 0 / std 1; RGB input.
        blob = cv2.dnn.blobFromImage(crop, 1.0, (self.size, self.size), (0, 0, 0), swapRB=True)
        pred = self.session.run(None, {self.input_name: blob})[0][0].reshape(-1, 2)
        pred = (pred + 1) * (self.size // 2)
        inv = cv2.invertAffineTransform(m)
        return pred @ inv[:, :2].T + inv[:, 2]

    @staticmethod
    def five(lmk106: np.ndarray) -> np.ndarray:
        return lmk106[LMK106_TO_5].astype(np.float32)
