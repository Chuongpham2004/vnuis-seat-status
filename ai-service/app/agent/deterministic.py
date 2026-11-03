"""Built-in deterministic tool-calling engine (default when no LLM key is configured).

Parses the intent with app.agent.text, calls the same tools an LLM would, and writes the
reply in the student's language. Fully offline and reproducible: ideal for CI and the defense.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

from app.agent import text as T
from app.agent.tools import ToolCall, ToolExecutor

RETRYABLE = {"SEAT_NOT_AVAILABLE", "SEAT_BUSY"}


@dataclass
class ChatResult:
    reply: str
    language: str
    engine: str
    intent: str | None = None
    tool_calls: list[ToolCall] = field(default_factory=list)
    suggested_seats: list[dict[str, Any]] | None = None
    reservation: dict[str, Any] | None = None


# ---------------------------------------------------------------------------- wording

def _features(seat: dict[str, Any], lang: str) -> str:
    parts = []
    if seat.get("quietZone"):
        parts.append("yên tĩnh" if lang == "vi" else "quiet")
    if seat.get("hasPowerOutlet"):
        parts.append("có ổ cắm" if lang == "vi" else "power outlet")
    if seat.get("seatType") == "GROUP_TABLE":
        parts.append("bàn nhóm" if lang == "vi" else "group table")
    return ", ".join(parts)


def _level_word(level: str, lang: str) -> str:
    vi = {"LOW": "vắng", "MEDIUM": "vừa phải", "HIGH": "đông"}
    en = {"LOW": "quiet", "MEDIUM": "moderately busy", "HIGH": "busy"}
    return (vi if lang == "vi" else en).get(level, level)


def _local_hhmm(iso: str | None, tz) -> str:
    if not iso:
        return ""
    return datetime.fromisoformat(iso.replace("Z", "+00:00")).astimezone(tz).strftime("%H:%M")


def booked_reply(res: dict[str, Any], seat: dict[str, Any] | None, lang: str, tz) -> str:
    minutes = max(1, round((res.get("secondsUntilExpiry") or 900) / 60))
    until = _local_hhmm(res.get("expireAt"), tz)
    feats = _features(seat, lang) if seat else ""
    if lang == "vi":
        where = f"khu {res['zoneCode']}" + (f" – {feats}" if feats else "")
        return (f"✅ Đã đặt ghế {res['seatCode']} ({where}) cho bạn. Ghế được giữ trong {minutes} phút"
                f"{f' (đến {until})' if until else ''}.\n"
                f"👉 Để check-in: đến ghế {res['seatCode']}, ngồi xuống rồi quét mã QR dán trên bàn. "
                "Hệ thống chỉ xác nhận khi cảm biến phát hiện bạn đã ngồi vào ghế. "
                "Nếu không check-in kịp, lượt đặt sẽ bị huỷ và bạn bị trừ 10 điểm uy tín.")
    where = f"zone {res['zoneCode']}" + (f", {feats}" if feats else "")
    return (f"✅ Seat {res['seatCode']} ({where}) is reserved for you for {minutes} minutes"
            f"{f' (until {until})' if until else ''}.\n"
            f"👉 To check in: go to {res['seatCode']}, sit down, then scan the QR code on the desk. "
            "Check-in only succeeds once the seat sensor detects you. "
            "If you do not check in in time the booking is released and 10 credit points are deducted.")


def error_reply(err: dict[str, Any], lang: str, seat_code: str | None = None) -> str:
    code = err.get("code", "")
    detail = err.get("detail", "")
    vi = lang == "vi"
    s = seat_code or ""
    if code in RETRYABLE:
        return f"Ghế {s} vừa có người khác đặt hoặc không còn trống." if vi else f"Seat {s} was just taken or is not available."
    if code == "USER_HAS_ACTIVE_RESERVATION":
        return ("Bạn đang có một lượt đặt chỗ chưa kết thúc. Hãy check-in hoặc huỷ lượt đó trước khi đặt ghế mới."
                if vi else "You already have an open reservation. Check in or cancel it before booking another seat.")
    if code == "USER_BANNED":
        return (f"Tài khoản của bạn đang tạm bị khoá đặt chỗ trước do vắng mặt (điểm uy tín dưới 60). {detail}"
                if vi else f"Advance booking is suspended for your account after a no-show (credit below 60). {detail}")
    if code == "INSUFFICIENT_CREDIT":
        return ("Điểm uy tín của bạn chưa đủ (cần ít nhất 60) để đặt chỗ trước." if vi
                else "Your credit score is too low (minimum 60) to book in advance.")
    if code == "SEAT_NOT_FOUND":
        return f"Không tìm thấy ghế {s}." if vi else f"There is no seat {s}."
    if err.get("status") == 401:
        return ("Phiên đăng nhập đã hết hạn, bạn hãy đăng nhập lại." if vi
                else "Your session has expired, please sign in again.")
    if code == "BACKEND_UNREACHABLE":
        return ("Hệ thống đặt chỗ đang tạm thời không phản hồi, bạn thử lại sau ít phút nhé." if vi
                else "The booking system is not responding right now, please try again in a minute.")
    return (f"Không thực hiện được yêu cầu ({code}): {detail}" if vi else f"Could not complete the request ({code}): {detail}")


def suggestions_reply(seats: list[dict[str, Any]], lang: str) -> str:
    lines = []
    for i, s in enumerate(seats, 1):
        feats = _features(s, lang)
        busy = s.get("zoneOccupancyPercent")
        if lang == "vi":
            lines.append(f"{i}. Ghế {s['seatCode']} – khu {s['zoneCode']}{', ' + feats if feats else ''}"
                         f" (khu đang kín {busy}%)")
        else:
            lines.append(f"{i}. Seat {s['seatCode']} – zone {s['zoneCode']}{', ' + feats if feats else ''}"
                         f" (zone {busy}% full)")
    if lang == "vi":
        return ("Mình tìm được các ghế trống phù hợp:\n" + "\n".join(lines)
                + "\nBạn muốn đặt ghế nào? Ví dụ: “đặt ghế đầu tiên” hoặc “đặt ghế " + seats[0]["seatCode"] + "”.")
    return ("Here are the best available seats:\n" + "\n".join(lines)
            + f"\nWhich one should I book? e.g. “book the first one” or “book {seats[0]['seatCode']}”.")


def no_seats_reply(lang: str) -> str:
    return ("Hiện không còn ghế trống nào đúng yêu cầu. Bạn thử bỏ bớt điều kiện (ví dụ không cần ổ cắm) "
            "hoặc hỏi mình khung giờ nào vắng hơn nhé." if lang == "vi"
            else "No free seat matches right now. Try fewer requirements, or ask me which hour will be quieter.")


def forecast_reply(data: dict[str, Any], lang: str, tz, now: datetime, requested_day: str = "today",
                   requested_hour: int | None = None) -> str:
    target = datetime.fromisoformat(data["target"]).astimezone(tz)
    tomorrow = target.date() > now.date()
    # "Chiều nay 15h" asked at 18:00: that hour is over, so say we are answering for tomorrow.
    note = ""
    if tomorrow and requested_day == "today" and requested_hour is not None:
        note = (f"{requested_hour:02d}:00 hôm nay đã qua, nên mình dự báo cho ngày mai. " if lang == "vi"
                else f"{requested_hour:02d}:00 today has passed, so here is tomorrow. ")
    when_vi = f"lúc {target:%H}:00 {'ngày mai' if tomorrow else 'hôm nay'}"
    when_en = f"at {target:%H}:00 {'tomorrow' if tomorrow else 'today'}"
    zones = data["zones"]
    if len(zones) == 1:
        z = zones[0]
        live = z.get("liveNow")
        if lang == "vi":
            msg = (f"Dự báo {when_vi}, khu {z['zoneCode']} sẽ kín khoảng {z['predictedPercent']}% chỗ ngồi "
                   f"({_level_word(z['level'], lang)}; khoảng {z['lowerPercent']}–{z['upperPercent']}%).")
            if live:
                msg += f" Hiện tại có {live['occupied']}/{live['total']} ghế đang có người, còn {live['available']} ghế trống."
            if z["level"] == "HIGH":
                msg += " Bạn nên đến sớm hoặc đặt chỗ trước."
            return note + msg
        msg = (f"Forecast {when_en}: zone {z['zoneCode']} about {z['predictedPercent']}% full "
               f"({_level_word(z['level'], lang)}; range {z['lowerPercent']}-{z['upperPercent']}%).")
        if live:
            msg += f" Right now {live['occupied']}/{live['total']} seats are in use and {live['available']} are free."
        if z["level"] == "HIGH":
            msg += " Consider coming early or booking ahead."
        return note + msg
    best = min(zones, key=lambda z: z["predictedPercent"])
    if lang == "vi":
        rows = "\n".join(f"• Khu {z['zoneCode']}: ~{z['predictedPercent']}% ({_level_word(z['level'], lang)})" for z in zones)
        return f"{note}Dự báo {when_vi}:\n{rows}\nKhu {best['zoneCode']} sẽ vắng nhất."
    rows = "\n".join(f"• Zone {z['zoneCode']}: ~{z['predictedPercent']}% ({_level_word(z['level'], lang)})" for z in zones)
    return f"{note}Forecast {when_en}:\n{rows}\nZone {best['zoneCode']} will be the quietest."


def help_reply(lang: str) -> str:
    if lang == "vi":
        return ("Xin chào! Mình là trợ lý thư viện. Mình có thể:\n"
                "• Tìm ghế trống theo yêu cầu – “tìm ghế yên tĩnh có ổ cắm”\n"
                "• Dự báo độ đông – “chiều nay 15h khu A có đông không?”\n"
                "• Đặt chỗ cho bạn – “đặt giúp mình ghế A-05” hoặc “tìm và đặt 1 ghế ở khu B”")
    return ("Hi! I'm the library assistant. I can:\n"
            "• find free seats – “find a quiet seat with a power outlet”\n"
            "• forecast how busy it gets – “is zone C busy at 3pm?”\n"
            "• book for you – “book seat A-05” or “find and book a seat in zone B”")


# ---------------------------------------------------------------------------- engine

class DeterministicAgent:
    name = "deterministic"

    def __init__(self, tz):
        self.tz = tz

    def respond(self, message: str, executor: ToolExecutor, suggestions: list[dict[str, Any]],
                now: datetime) -> ChatResult:
        p = T.parse(message, now, has_suggestions=bool(suggestions))
        lang = p.language
        result = ChatResult(reply="", language=lang, engine=self.name, intent=p.intent)

        if p.intent == T.BOOK_SEAT:
            self._book(result, executor, [{"seatCode": p.seat_code, "zoneCode": p.seat_code[0]}], retry=False)
        elif p.intent == T.BOOK_SUGGESTED:
            ordered = list(suggestions)
            if p.ordinal is not None and p.ordinal < len(ordered):
                ordered = [ordered[p.ordinal]] + [s for i, s in enumerate(ordered) if i != p.ordinal]
            self._book(result, executor, ordered, retry=p.ordinal is None)
        elif p.intent in (T.FIND_AND_BOOK, T.FIND):
            call = executor.call("find_optimal_seats", {
                "zone_code": p.zone_code, "is_quiet_zone": p.quiet, "has_power_outlet": p.power,
                "seat_type": p.seat_type, "limit": 3})
            if not call.ok:
                result.reply = error_reply(call.error or {}, lang)
            elif not call.result:
                result.reply = no_seats_reply(lang)
            else:
                result.suggested_seats = call.result
                if p.intent == T.FIND_AND_BOOK:
                    self._book(result, executor, call.result, retry=True)
                else:
                    result.reply = suggestions_reply(call.result, lang)
        elif p.intent == T.FORECAST:
            call = executor.call("get_occupancy_forecast", {
                "zone_code": p.zone_code, "target_hour": p.target_hour, "day": p.day})
            result.reply = forecast_reply(call.result, lang, self.tz, now, p.day, p.target_hour) if call.ok \
                else error_reply(call.error or {}, lang)
        else:
            result.reply = help_reply(lang)

        result.tool_calls = list(executor.trace)
        return result

    def _book(self, result: ChatResult, executor: ToolExecutor, candidates: list[dict[str, Any]], retry: bool) -> None:
        last_error: dict[str, Any] | None = None
        tried = None
        for seat in candidates[:3] if retry else candidates[:1]:
            tried = seat["seatCode"]
            call = executor.call("reserve_seat", {"seat_code": tried})
            if call.ok:
                result.reservation = call.result
                result.reply = booked_reply(call.result, seat if "quietZone" in seat else None, result.language, self.tz)
                return
            last_error = call.error or {}
            if last_error.get("code") not in RETRYABLE:
                break
        result.reply = error_reply(last_error or {}, result.language, tried)
        if retry and last_error and last_error.get("code") in RETRYABLE:
            result.reply += (" Các ghế gợi ý đều vừa được đặt, bạn thử tìm lại nhé." if result.language == "vi"
                             else " All suggested seats were just taken, please search again.")
