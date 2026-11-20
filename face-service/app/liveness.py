"""Head-turn challenge.

A flat photo or a phone screen rotated in front of the camera keeps the nose on the eye
midline (no parallax), so it never reaches the yaw a real head turn produces.
"""

from __future__ import annotations

import numpy as np

CENTER, LEFT, RIGHT = "CENTER", "LEFT", "RIGHT"
STEPS = (CENTER, LEFT, RIGHT)


def yaw_ratio(five: np.ndarray) -> float:
    """(nose.x - eye_mid.x) / eye distance.

    > 0: the student turned to *their* left (on an un-mirrored camera frame their nose
    moves towards the image's right edge).
    """
    left_eye, right_eye, nose = five[0], five[1], five[2]
    eye_dist = float(np.linalg.norm(right_eye - left_eye))
    if eye_dist < 1e-3:
        return 0.0
    return float((nose[0] - (left_eye[0] + right_eye[0]) / 2) / eye_dist)


def classify_pose(yaw: float, center_max: float, turn_min: float) -> str | None:
    if abs(yaw) <= center_max:
        return CENTER
    if yaw >= turn_min:
        return LEFT
    if yaw <= -turn_min:
        return RIGHT
    return None  # in between: not clearly any step


def check_sequence(required: list[str], frames: list[tuple[str, str | None]]) -> tuple[bool, str | None]:
    """frames = [(step the frame was captured for, pose actually seen)] in capture order.

    Each required step needs a frame captured for it that shows that pose, in challenge order.
    """
    cursor = 0
    for i, step in enumerate(required):
        hit = next((j for j in range(cursor, len(frames)) if frames[j] == (step, step)), None)
        if hit is None:
            return False, f"step {i + 1} ({step}) not observed"
        cursor = hit + 1
    return True, None
