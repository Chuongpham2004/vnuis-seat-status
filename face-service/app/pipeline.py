"""Frames of one challenge -> per-frame pose + liveness verdict + identity embedding.

Detection (YOLOv8-face) -> 106 landmarks -> 5 points -> yaw + ArcFace alignment -> embedding.
Matching against the enrolled template is backend-core's job; this service never stores faces.
"""

from __future__ import annotations

import os
import time
from dataclasses import dataclass, field
from typing import Any

import numpy as np

from app.align import norm_crop
from app.config import Settings
from app.detector import Detection, YoloFaceDetector
from app.embedder import ArcFaceEmbedder, l2norm
from app.landmarks import Landmark106
from app.liveness import CENTER, check_sequence, classify_pose, yaw_ratio

MODEL_NAME = "yolov8n-face+2d106det+w600k_mbf"

# Result statuses (backend-core maps them to API error codes).
OK = "OK"
NO_FACE = "NO_FACE"
MULTIPLE_FACES = "MULTIPLE_FACES"
LIVENESS_FAILED = "LIVENESS_FAILED"


@dataclass
class FrameResult:
    step: str
    faces: int
    det_score: float | None = None
    yaw: float | None = None
    pose: str | None = None
    embedding: np.ndarray | None = field(default=None, repr=False)

    def public(self) -> dict[str, Any]:
        return {"step": self.step, "faces": self.faces, "detScore": _r(self.det_score), "yaw": _r(self.yaw),
                "pose": self.pose}


def _r(v: float | None) -> float | None:
    return None if v is None else round(v, 4)


def main_faces(dets: list[Detection], min_px: int) -> list[Detection]:
    """Faces that matter: big enough, and at least half the size of the biggest one
    (students in the background of a library are fine, a second person at the camera is not)."""
    big = [d for d in dets if d.short_side >= min_px]
    if not big:
        return []
    largest = max(d.short_side for d in big)
    return [d for d in big if d.short_side >= 0.5 * largest]


class FacePipeline:
    def __init__(self, settings: Settings, detector, landmarker, embedder):
        self.s = settings
        self.detector = detector
        self.landmarker = landmarker
        self.embedder = embedder

    @classmethod
    def load(cls, settings: Settings) -> "FacePipeline":
        import onnxruntime as ort

        opts = ort.SessionOptions()
        opts.intra_op_num_threads = settings.onnx_threads

        def session(name: str):
            path = os.path.join(settings.model_dir, name)
            if not os.path.exists(path):
                raise FileNotFoundError(f"model {path} missing - run scripts/download_models.py")
            return ort.InferenceSession(path, opts, providers=["CPUExecutionProvider"])

        return cls(settings,
                   YoloFaceDetector(session(settings.detector_model), settings.det_conf, settings.det_iou),
                   Landmark106(session(settings.landmark_model)),
                   ArcFaceEmbedder(session(settings.embedder_model)))

    def frame(self, step: str, img: np.ndarray) -> FrameResult:
        faces = main_faces(self.detector.detect(img), self.s.min_face_px)
        r = FrameResult(step=step, faces=len(faces))
        if len(faces) != 1:
            return r
        d = faces[0]
        five = Landmark106.five(self.landmarker.predict(img, d.box))
        r.det_score = d.score
        r.yaw = yaw_ratio(five)
        r.pose = classify_pose(r.yaw, self.s.yaw_center_max, self.s.yaw_turn_min)
        r.embedding = self.embedder.embed(norm_crop(img, five))
        return r

    def analyze(self, steps: list[str], frames: list[tuple[str, np.ndarray]]) -> dict[str, Any]:
        t0 = time.perf_counter()
        results = [self.frame(step, img) for step, img in frames]
        status, reason, embedding, consistency = self._verdict(steps, results)
        return {
            "status": status,
            "reason": reason,
            "embedding": None if embedding is None else [round(float(x), 6) for x in embedding],
            "consistency": _r(consistency),
            "frames": [r.public() for r in results],
            "model": MODEL_NAME,
            "latencyMs": int((time.perf_counter() - t0) * 1000),
        }

    def _verdict(self, steps: list[str], results: list[FrameResult]):
        crowded = sum(1 for r in results if r.faces > 1)
        if crowded:
            return MULTIPLE_FACES, f"{crowded} frame(s) show more than one face", None, None
        frontal = [r.embedding for r in results if r.pose == CENTER and r.embedding is not None]
        if not frontal:
            return NO_FACE, "no frontal face found", None, None
        ok, why = check_sequence(steps, [(r.step, r.pose) for r in results])
        if not ok:
            return LIVENESS_FAILED, why, None, None
        template = l2norm(np.mean(frontal, axis=0))
        sims = [float(template @ r.embedding) for r in results if r.embedding is not None]
        consistency = min(sims)
        if consistency < self.s.consistency_min:
            return LIVENESS_FAILED, "face changed during the challenge", None, consistency
        return OK, None, template, consistency
