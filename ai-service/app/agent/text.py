"""Intent and slot extraction for Vietnamese (with or without accents) and English messages."""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from datetime import datetime

# Intents
BOOK_SEAT = "BOOK_SEAT"            # "Đặt giúp mình ghế A-05"
FIND_AND_BOOK = "FIND_AND_BOOK"    # "Tìm và đặt giúp mình 1 ghế yên tĩnh có ổ cắm"
BOOK_SUGGESTED = "BOOK_SUGGESTED"  # "đặt ghế đầu tiên" after a suggestion list
FIND = "FIND"                      # "Còn ghế trống có ổ cắm không?"
FORECAST = "FORECAST"              # "Chiều nay 15h khu A có đông không?"
HELP = "HELP"
UNKNOWN = "UNKNOWN"


def strip_accents(text: str) -> str:
    text = text.replace("đ", "d").replace("Đ", "D")
    decomposed = unicodedata.normalize("NFD", text)
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


def normalize(text: str) -> str:
    t = strip_accents(text.lower())
    t = re.sub(r"[^\w:\-\s]", " ", t)
    return re.sub(r"\s+", " ", t).strip()


_VI_WORDS = {"minh", "giup", "ghe", "khu", "cho", "khong", "dat", "tim", "nay", "chieu", "sang", "gio", "nhe",
             "voi", "ban", "toi", "duoc", "con", "co", "trong", "cam", "dien", "oi", "di", "luon", "nao"}


def detect_language(raw: str) -> str:
    if strip_accents(raw) != raw:
        return "vi"  # any Vietnamese diacritic (or đ)
    words = set(normalize(raw).split())
    return "vi" if len(words & _VI_WORDS) >= 2 else "en"


def _has(t: str, *phrases: str) -> bool:
    return any(re.search(rf"(?<!\w){re.escape(p)}(?!\w)", t) for p in phrases)


_SEAT_PATTERNS = [
    re.compile(r"(?<!\w)([abc])\s*-\s*(\d{1,2})(?!\w)"),          # A-05, a - 5
    re.compile(r"(?<!\w)([abc])(\d{2})(?!\w)"),                   # A05
    re.compile(r"(?<!\w)(?:ghe|seat|cho|ban)\s+(?:so\s+)?([abc])\s*(\d{1,2})(?!\w)"),  # ghế A 5, seat b 7
]
_ZONE = re.compile(r"(?<!\w)(?:khu(?:\s+vuc)?|zone|area|section)\s+([abc])(?!\w)")


@dataclass
class ParsedMessage:
    language: str
    intent: str
    seat_code: str | None = None
    zone_code: str | None = None
    quiet: bool | None = None
    power: bool | None = None
    seat_type: str | None = None
    target_hour: int | None = None
    day: str = "today"
    ordinal: int | None = None

    @property
    def has_criteria(self) -> bool:
        return any(v is not None for v in (self.zone_code, self.quiet, self.power, self.seat_type))


def parse_seat(t: str) -> str | None:
    for pattern in _SEAT_PATTERNS:
        m = pattern.search(t)
        if m and 1 <= int(m.group(2)) <= 20:
            return f"{m.group(1).upper()}-{int(m.group(2)):02d}"
    return None


def parse_time(t: str, raw_lower: str, now: datetime) -> tuple[int | None, str]:
    """Returns (local hour or None, 'today'|'tomorrow')."""
    day = "tomorrow" if _has(t, "ngay mai", "mai", "tomorrow", "sang mai", "chieu mai", "toi mai") else "today"
    if _has(t, "bay gio", "hien tai", "luc nay", "ngay bay gio", "right now", "now", "currently"):
        return now.hour, day

    hour: int | None = None
    m = re.search(r"(?<!\w)(\d{1,2})\s*(am|pm)(?!\w)", t)
    if m:
        hour = int(m.group(1)) % 12 + (12 if m.group(2) == "pm" else 0)
    if hour is None:
        m = re.search(r"(?<!\w)(\d{1,2})\s*(?:h|gio)(?:\s*\d{1,2})?(?!\w)", t) or re.search(r"(?<!\w)(\d{1,2}):\d{2}(?!\w)", t)
        if m:
            hour = int(m.group(1))

    # "tôi" (I) and "tối" (evening) both become "toi" without accents: check the accented text first.
    evening = "tối" in raw_lower or _has(t, "toi nay", "buoi toi", "toi mai", "evening", "tonight")
    afternoon = _has(t, "chieu", "afternoon")
    morning = _has(t, "sang", "morning")
    noon = _has(t, "trua", "noon", "lunch")
    if hour is not None:
        if (evening or afternoon) and hour < 12:
            hour += 12
    elif afternoon:
        hour = 15
    elif evening:
        hour = 19
    elif morning:
        hour = 9
    elif noon:
        hour = 12
    if hour is not None and not 0 <= hour <= 23:
        hour = None
    return hour, day


def parse(raw: str, now: datetime, has_suggestions: bool = False) -> ParsedMessage:
    t = normalize(raw)
    raw_lower = raw.lower()
    p = ParsedMessage(language=detect_language(raw), intent=UNKNOWN)

    p.seat_code = parse_seat(t)
    zone = _ZONE.search(t)
    p.zone_code = zone.group(1).upper() if zone else None
    if _has(t, "yen tinh", "quiet", "im lang", "silent", "tinh lang", "khong on", "it on", "tap trung"):
        p.quiet = True
    if _has(t, "o cam", "o dien", "cam dien", "cho cam", "cam sac", "sac", "sac pin", "sac laptop", "power",
            "outlet", "plug", "socket", "charger", "charging", "charge"):
        p.power = True
    if _has(t, "nhom", "hoc nhom", "ban nhom", "group", "team", "ban lon"):
        p.seat_type = "GROUP_TABLE"
    elif _has(t, "ca nhan", "mot minh", "single", "solo", "rieng"):
        p.seat_type = "SINGLE"
    p.target_hour, p.day = parse_time(t, raw_lower, now)

    if _has(t, "dau tien", "thu nhat", "so 1", "first", "cai dau", "ghe 1"):
        p.ordinal = 0
    elif _has(t, "thu hai", "so 2", "second", "ghe 2"):
        p.ordinal = 1
    elif _has(t, "thu ba", "so 3", "third", "ghe 3"):
        p.ordinal = 2

    book = _has(t, "dat", "book", "reserve", "giu", "giu cho", "dang ky")
    find = _has(t, "tim", "find", "search", "look for", "looking for", "goi y", "recommend", "suggest", "con trong",
                "con ghe", "con cho", "ghe trong", "cho trong", "available", "free seat", "empty seat", "any seat",
                "ghe nao", "cho nao", "where can i sit")
    forecast = any(w in raw_lower for w in ("đông", "vắng", "dự báo")) or _has(
        t, "dong khong", "dong k", "dong ko", "co dong", "dong nguoi", "dong lam", "vang khong", "du bao", "busy",
        "crowded", "forecast", "how full", "packed", "occupancy", "bao nhieu nguoi", "it nguoi", "nhieu nguoi",
        "ty le lap day")
    confirm = _has(t, "ok", "oke", "okay", "yes", "yep", "dong y", "duoc", "chot", "lay", "ghe do", "cho do",
                   "that one", "book it", "dat di", "dat luon", "dat no", "it")
    greeting = _has(t, "xin chao", "chao", "hello", "hi", "hey", "help", "giup gi", "lam duoc gi", "lam gi",
                    "what can you do", "huong dan")

    if p.seat_code and book:
        p.intent = BOOK_SEAT
    elif p.seat_code and not forecast:
        # "ghế A-05 còn trống không?" -> show what is free around that seat, do not book.
        p.intent = FIND
        p.zone_code = p.zone_code or p.seat_code[0]
    elif book and has_suggestions and not p.has_criteria and not find and (p.ordinal is not None or confirm):
        p.intent = BOOK_SUGGESTED
    elif book:
        p.intent = FIND_AND_BOOK
    elif forecast or (p.target_hour is not None and not find and not p.has_criteria):
        p.intent = FORECAST
    elif find or p.has_criteria:
        p.intent = FIND
    elif has_suggestions and (confirm or p.ordinal is not None):
        p.intent = BOOK_SUGGESTED
    elif greeting:
        p.intent = HELP
    return p
