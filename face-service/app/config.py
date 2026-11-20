"""Settings from environment variables (names match infra/docker-compose.yml)."""

from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore", protected_namespaces=())

    # Shared secret with backend-core (header X-Internal-Key). Empty = no check (local dev only).
    internal_key: str = ""

    model_dir: str = "models"
    detector_model: str = "yolov8n-face.onnx"     # lindevs/yolov8-face (WIDER FACE)
    landmark_model: str = "2d106det.onnx"         # InsightFace buffalo_s, 106 points
    embedder_model: str = "w600k_mbf.onnx"        # InsightFace buffalo_s, ArcFace MobileFaceNet
    onnx_threads: int = 2

    # ---- detection ----
    det_conf: float = 0.5
    det_iou: float = 0.45
    min_face_px: int = 60  # shorter bbox side; smaller faces are too blurry to trust

    # ---- liveness (head-turn challenge) ----
    # yaw = (nose.x - eye_mid.x) / eye distance on the raw (un-mirrored) camera frame;
    # > 0 means the student turned to *their* left.
    yaw_center_max: float = 0.12
    yaw_turn_min: float = 0.25
    # Every frame must belong to the same person as the frontal mean (turned faces score lower).
    consistency_min: float = 0.30

    # ---- request limits ----
    max_frames: int = 12
    max_frame_bytes: int = 400_000
