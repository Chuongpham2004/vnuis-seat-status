from datetime import datetime
from zoneinfo import ZoneInfo

import pytest

from app.agent import text as T

NOW = datetime(2026, 11, 3, 10, 20, tzinfo=ZoneInfo("Asia/Ho_Chi_Minh"))


@pytest.mark.parametrize("msg, intent, fields", [
    ("Tìm và đặt giúp mình 1 ghế trống ở khu yên tĩnh có ổ cắm điện", T.FIND_AND_BOOK,
     {"quiet": True, "power": True, "language": "vi"}),
    ("Chiều nay 15h khu A có đông không?", T.FORECAST, {"zone_code": "A", "target_hour": 15, "day": "today"}),
    ("Đặt giúp mình ghế A-05", T.BOOK_SEAT, {"seat_code": "A-05"}),
    ("dat giup minh ghe a05", T.BOOK_SEAT, {"seat_code": "A-05", "language": "vi"}),  # typed without accents
    ("tim ghe co o cam o khu b", T.FIND, {"zone_code": "B", "power": True}),
    ("Tối nay 8 giờ khu C có vắng không", T.FORECAST, {"zone_code": "C", "target_hour": 20}),
    ("Tôi muốn tìm bàn nhóm", T.FIND, {"seat_type": "GROUP_TABLE", "target_hour": None}),  # "tôi" is not "tối"
    ("Sáng mai khu B có đông không", T.FORECAST, {"zone_code": "B", "target_hour": 9, "day": "tomorrow"}),
    ("Find me a quiet seat with a power outlet", T.FIND, {"quiet": True, "power": True, "language": "en"}),
    ("Is zone C busy at 3pm tomorrow?", T.FORECAST, {"zone_code": "C", "target_hour": 15, "day": "tomorrow"}),
    ("book seat b07", T.BOOK_SEAT, {"seat_code": "B-07", "language": "en"}),
    ("Book me any seat in zone B", T.FIND_AND_BOOK, {"zone_code": "B"}),
    ("ghế A-05 còn trống không?", T.FIND, {"zone_code": "A"}),
    ("xin chào", T.HELP, {}),
])
def test_parse(msg, intent, fields):
    p = T.parse(msg, NOW)
    assert p.intent == intent, p
    for k, v in fields.items():
        assert getattr(p, k) == v, (k, p)


def test_follow_up_booking_needs_previous_suggestions():
    assert T.parse("đặt ghế đầu tiên", NOW, has_suggestions=True).intent == T.BOOK_SUGGESTED
    assert T.parse("đặt ghế đầu tiên", NOW, has_suggestions=True).ordinal == 0
    assert T.parse("ok book it", NOW, has_suggestions=True).intent == T.BOOK_SUGGESTED
    assert T.parse("đặt ghế thứ hai", NOW, has_suggestions=True).ordinal == 1


def test_seat_numbers_out_of_range_are_ignored():
    assert T.parse_seat("ghe a-35") is None


def test_hour_without_seat_is_not_mistaken_for_a_seat_code():
    p = T.parse("khu a 15h có đông không", NOW)
    assert p.seat_code is None and p.zone_code == "A" and p.target_hour == 15
