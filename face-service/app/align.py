"""Similarity-align a face to the canonical 112x112 ArcFace template."""

from __future__ import annotations

import cv2
import numpy as np

ARCFACE_DST = np.array([[38.2946, 51.6963], [73.5318, 51.5014], [56.0252, 71.7366],
                        [41.5493, 92.3655], [70.7299, 92.2041]], dtype=np.float32)


def norm_crop(img: np.ndarray, five: np.ndarray, size: int = 112) -> np.ndarray:
    dst = ARCFACE_DST * (size / 112.0)
    m, _ = cv2.estimateAffinePartial2D(five.astype(np.float32), dst, method=cv2.LMEDS)
    if m is None:
        raise ValueError("cannot estimate alignment")
    return cv2.warpAffine(img, m, (size, size), borderValue=0)
