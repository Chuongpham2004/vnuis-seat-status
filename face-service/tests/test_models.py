"""End-to-end checks with the real ONNX models (skipped when models/ is empty, e.g. in CI)."""

import cv2
import numpy as np
import pytest

from app.liveness import CENTER, LEFT
from app.pipeline import MULTIPLE_FACES, OK, LIVENESS_FAILED, FacePipeline
from tests.conftest import models_present

pytestmark = pytest.mark.skipif(not models_present(), reason="run scripts/download_models.py first")


@pytest.fixture(scope="module")
def pipeline():
    from app.config import Settings
    from tests.conftest import MODEL_DIR

    return FacePipeline.load(Settings(model_dir=MODEL_DIR))


def test_single_frontal_face(pipeline, face_img):
    r = pipeline.frame(CENTER, face_img)
    assert r.faces == 1 and r.pose == CENTER and r.det_score > 0.6
    assert r.embedding.shape == (512,) and np.linalg.norm(r.embedding) == pytest.approx(1, abs=1e-4)


def test_same_person_matches_across_flip_and_lighting(pipeline, face_img):
    a = pipeline.frame(CENTER, face_img).embedding
    b = pipeline.frame(CENTER, cv2.flip(face_img, 1)).embedding
    c = pipeline.frame(CENTER, (face_img * 0.55).astype(np.uint8)).embedding
    assert float(a @ b) > 0.8 and float(a @ c) > 0.8


def test_flip_negates_yaw(pipeline, face_img):
    y1 = pipeline.frame(CENTER, face_img).yaw
    y2 = pipeline.frame(CENTER, cv2.flip(face_img, 1)).yaw
    assert y1 == pytest.approx(-y2, abs=0.03)


def test_no_face_in_blank_frame(pipeline):
    assert pipeline.frame(CENTER, np.full((480, 640, 3), 128, np.uint8)).faces == 0


def test_two_people_at_the_camera(pipeline, face_img):
    res = pipeline.analyze([CENTER], [(CENTER, np.hstack([face_img, face_img]))])
    assert res["status"] == MULTIPLE_FACES


def test_static_photo_cannot_turn(pipeline, face_img):
    res = pipeline.analyze([CENTER, LEFT], [(CENTER, face_img), (LEFT, face_img), (LEFT, face_img)])
    assert res["status"] == LIVENESS_FAILED


def test_frontal_only_challenge_ok(pipeline, face_img):
    res = pipeline.analyze([CENTER], [(CENTER, face_img), (CENTER, face_img)])
    assert res["status"] == OK and len(res["embedding"]) == 512 and res["consistency"] > 0.99
