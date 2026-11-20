import numpy as np
import pytest

from app.config import Settings
from app.embedder import l2norm
from app.liveness import CENTER, LEFT, RIGHT, check_sequence, classify_pose, yaw_ratio
from app.pipeline import LIVENESS_FAILED, MULTIPLE_FACES, NO_FACE, OK, FacePipeline, FrameResult


def five(nose_dx: float) -> np.ndarray:
    # eyes 40 px apart, nose shifted horizontally by nose_dx
    return np.array([[100, 100], [140, 100], [120 + nose_dx, 120], [105, 140], [135, 140]], dtype=np.float32)


def test_yaw_ratio_sign_and_scale():
    assert yaw_ratio(five(0)) == pytest.approx(0)
    assert yaw_ratio(five(12)) == pytest.approx(0.3)
    assert yaw_ratio(five(-12)) == pytest.approx(-0.3)


def test_yaw_ratio_degenerate_eyes():
    pts = five(5)
    pts[1] = pts[0]
    assert yaw_ratio(pts) == 0.0


@pytest.mark.parametrize("yaw,pose", [(0.0, CENTER), (0.1, CENTER), (-0.12, CENTER), (0.2, None),
                                      (0.3, LEFT), (-0.3, RIGHT), (-0.18, None)])
def test_classify_pose(yaw, pose):
    assert classify_pose(yaw, 0.12, 0.25) == pose


def test_sequence_in_order():
    ok, _ = check_sequence([CENTER, LEFT, RIGHT],
                           [(CENTER, CENTER), (CENTER, None), (LEFT, None), (LEFT, LEFT), (RIGHT, RIGHT)])
    assert ok


def test_sequence_missing_step():
    ok, why = check_sequence([CENTER, LEFT, RIGHT], [(CENTER, CENTER), (LEFT, CENTER), (RIGHT, RIGHT)])
    assert not ok and "LEFT" in why


def test_sequence_pose_shown_under_wrong_step_does_not_count():
    # turning right while the app asked for LEFT must not satisfy LEFT
    ok, _ = check_sequence([CENTER, LEFT], [(CENTER, CENTER), (LEFT, RIGHT)])
    assert not ok


def test_sequence_wrong_order():
    ok, _ = check_sequence([CENTER, RIGHT, LEFT], [(CENTER, CENTER), (LEFT, LEFT), (RIGHT, RIGHT)])
    assert not ok


# ---- verdict (pipeline without models) ----

def emb(seed: int) -> np.ndarray:
    return l2norm(np.random.default_rng(seed).normal(size=512).astype(np.float32))


def verdict(steps, results):
    return FacePipeline(Settings(), None, None, None)._verdict(steps, results)


def fr(step, pose, e, faces=1):
    return FrameResult(step=step, faces=faces, det_score=0.9, yaw=0.0, pose=pose, embedding=e)


def test_verdict_ok_returns_unit_template():
    e = emb(1)
    status, reason, template, consistency = verdict([CENTER, LEFT],
                                                    [fr(CENTER, CENTER, e), fr(LEFT, LEFT, e)])
    assert status == OK and reason is None
    assert np.linalg.norm(template) == pytest.approx(1, abs=1e-5)
    assert consistency == pytest.approx(1, abs=1e-5)


def test_verdict_multiple_faces_wins():
    status, *_ = verdict([CENTER], [fr(CENTER, None, None, faces=2), fr(CENTER, CENTER, emb(1))])
    assert status == MULTIPLE_FACES


def test_verdict_no_frontal_face():
    status, *_ = verdict([CENTER], [fr(CENTER, None, None, faces=0)])
    assert status == NO_FACE


def test_verdict_static_photo_fails_liveness():
    e = emb(1)
    status, reason, template, _ = verdict([CENTER, LEFT, RIGHT],
                                          [fr(CENTER, CENTER, e), fr(LEFT, CENTER, e), fr(RIGHT, CENTER, e)])
    assert status == LIVENESS_FAILED and template is None


def test_verdict_person_swapped_mid_challenge():
    status, reason, *_ = verdict([CENTER, LEFT], [fr(CENTER, CENTER, emb(1)), fr(LEFT, LEFT, emb(2))])
    assert status == LIVENESS_FAILED and "changed" in reason
