"""ArcFace (InsightFace w600k_mbf) -> L2-normalised 512-d embedding."""

from __future__ import annotations

import cv2
import numpy as np


def l2norm(v: np.ndarray) -> np.ndarray:
    n = float(np.linalg.norm(v))
    return v / n if n > 0 else v


class ArcFaceEmbedder:
    def __init__(self, session):
        self.session = session
        self.input_name = session.get_inputs()[0].name

    def embed(self, aligned112: np.ndarray) -> np.ndarray:
        blob = cv2.dnn.blobFromImage(aligned112, 1 / 127.5, (112, 112), (127.5, 127.5, 127.5), swapRB=True)
        return l2norm(self.session.run(None, {self.input_name: blob})[0][0].astype(np.float32))
