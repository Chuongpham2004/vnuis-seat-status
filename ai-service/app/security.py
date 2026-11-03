"""Verifies the JWT issued by backend-core (same HMAC secret)."""

from __future__ import annotations

from dataclasses import dataclass

import jwt
from fastapi import HTTPException, status

# jjwt picks HS256/384/512 from the key length, so accept all three.
ALGORITHMS = ["HS256", "HS384", "HS512"]


@dataclass(frozen=True)
class Caller:
    user_id: str
    student_code: str | None
    role: str
    token: str  # forwarded verbatim to backend-core

    @property
    def is_admin(self) -> bool:
        return self.role == "ADMIN"


def _unauthorized(detail: str) -> HTTPException:
    return HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail={"code": "UNAUTHENTICATED", "detail": detail},
                         headers={"WWW-Authenticate": "Bearer"})


def caller_from_header(authorization: str | None, secret: str) -> Caller:
    if not authorization:
        raise _unauthorized("Missing Authorization: Bearer <token>")
    token = authorization[7:].strip() if authorization.lower().startswith("bearer ") else authorization.strip()
    try:
        claims = jwt.decode(token, secret.encode("utf-8"), algorithms=ALGORITHMS)
    except jwt.PyJWTError as exc:
        raise _unauthorized(f"Invalid access token ({exc.__class__.__name__})") from None
    return Caller(user_id=str(claims["sub"]), student_code=claims.get("sc"), role=claims.get("role", "STUDENT"),
                  token=token)
