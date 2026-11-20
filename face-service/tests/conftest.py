import os

import cv2
import numpy as np
import pytest

from app.config import Settings
from app.imaging import encode_frame

HERE = os.path.dirname(__file__)
MODEL_DIR = os.environ.get("FACE_TEST_MODEL_DIR", os.path.join(HERE, "..", "models"))


@pytest.fixture
def settings() -> Settings:
    return Settings(model_dir=MODEL_DIR, internal_key="")


@pytest.fixture
def blank_b64() -> str:
    return encode_frame(np.full((120, 160, 3), 200, dtype=np.uint8))


@pytest.fixture
def face_img() -> np.ndarray:
    # NASA portrait of Eileen Collins (public domain), via scikit-image's "astronaut".
    return cv2.imread(os.path.join(HERE, "fixtures", "astronaut.jpg"))


def models_present() -> bool:
    s = Settings()
    return all(os.path.exists(os.path.join(MODEL_DIR, m)) for m in (s.detector_model, s.landmark_model,
                                                                   s.embedder_model))
