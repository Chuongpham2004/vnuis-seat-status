from types import SimpleNamespace

from app.agent.llm import AnthropicProvider, AssistantTurn, OpenAICompatProvider, ToolRequest
from app.agent.memory import SessionStore
from app.agent.service import ChatService

from conftest import tool_names


def test_find_and_book_quiet_seat_with_outlet_in_vietnamese(chat_service, caller, backend):
    resp = chat_service.chat(caller, "Tìm và đặt giúp mình 1 ghế trống ở khu yên tĩnh có ổ cắm điện")
    assert resp["engine"] == "deterministic" and resp["intent"] == "FIND_AND_BOOK" and resp["language"] == "vi"
    assert tool_names(resp) == ["find_optimal_seats", "reserve_seat"]
    find_args = resp["toolCalls"][0]["arguments"]
    assert find_args["is_quiet_zone"] is True and find_args["has_power_outlet"] is True
    assert resp["reservation"]["seatCode"] == "A-03"  # first quiet seat with an outlet
    assert "A-03" in resp["reply"] and "QR" in resp["reply"] and "15 phút" in resp["reply"]
    # Every backend call carried the student's own JWT
    assert all(r.headers["Authorization"] == f"Bearer {caller.token}" for r in backend.requests)


def test_find_and_book_tries_the_next_seat_when_one_was_just_taken(chat_service, caller, backend):
    original = backend.handle

    def first_seat_race(request):
        if request.url.path.endswith("/reservations") and b"A-03" in request.content:
            return backend._problem(409, "SEAT_NOT_AVAILABLE", "Seat A-03 is RESERVED")
        return original(request)

    backend.handle = first_seat_race
    service = ChatService(chat_service.settings, chat_service.forecasts, chat_service.sessions, None,
                          backend_transport=backend.transport())
    resp = service.chat(caller, "find and book a quiet seat with a power outlet")
    assert tool_names(resp) == ["find_optimal_seats", "reserve_seat", "reserve_seat"]
    assert resp["reservation"]["seatCode"] == "A-05"
    assert resp["language"] == "en" and "scan the QR code" in resp["reply"]


def test_book_a_named_seat(chat_service, caller):
    resp = chat_service.chat(caller, "Đặt giúp mình ghế A-05")
    assert tool_names(resp) == ["reserve_seat"] and resp["reservation"]["seatCode"] == "A-05"


def test_suggest_then_book_from_the_session(chat_service, caller):
    first = chat_service.chat(caller, "Tìm ghế có ổ cắm")
    assert first["intent"] == "FIND" and first["reservation"] is None
    assert [s["seatCode"] for s in first["suggestedSeats"]][:2] == ["A-03", "A-05"]
    second = chat_service.chat(caller, "đặt ghế thứ hai", session_id=first["sessionId"])
    assert second["intent"] == "BOOK_SUGGESTED"
    assert second["reservation"]["seatCode"] == "A-05"


def test_forecast_question(chat_service, caller):
    resp = chat_service.chat(caller, "Chiều nay 15h khu A có đông không?")
    assert tool_names(resp) == ["get_occupancy_forecast"]
    args = resp["toolCalls"][0]["arguments"]
    assert args["zone_code"] == "A" and args["target_hour"] == 15
    result = resp["toolCalls"][0]["result"]["zones"][0]
    assert 0 <= result["predictedPercent"] <= 100 and result["liveNow"]["total"] == 20
    assert "khu A" in resp["reply"] and "15:00" in resp["reply"]


def test_forecast_for_an_hour_that_already_passed_says_it_is_for_tomorrow():
    from datetime import datetime
    from zoneinfo import ZoneInfo

    from app.agent.deterministic import forecast_reply

    tz = ZoneInfo("Asia/Ho_Chi_Minh")
    now = datetime(2026, 11, 3, 18, 0, tzinfo=tz)
    data = {"target": "2026-11-04T15:00:00+07:00", "zones": [
        {"zoneCode": "A", "predictedPercent": 70, "level": "MEDIUM", "lowerPercent": 64, "upperPercent": 76,
         "liveNow": None}]}
    reply = forecast_reply(data, "vi", tz, now, requested_day="today", requested_hour=15)
    assert reply.startswith("15:00 hôm nay đã qua") and "ngày mai" in reply
    assert "has passed" in forecast_reply(data, "en", tz, now, requested_day="today", requested_hour=15)
    assert "đã qua" not in forecast_reply(data, "vi", tz, now, requested_day="tomorrow", requested_hour=15)


def test_business_errors_are_explained(chat_service, caller, backend):
    backend.banned = True
    resp = chat_service.chat(caller, "Đặt giúp mình ghế A-05")
    assert resp["toolCalls"][0]["ok"] is False and resp["toolCalls"][0]["error"]["code"] == "USER_BANNED"
    assert "khoá" in resp["reply"] and resp["reservation"] is None

    backend.banned = False
    backend.reserved_by_user = "B-04"
    resp = chat_service.chat(caller, "book seat A-02")
    assert "already have an open reservation" in resp["reply"]


# ---------------------------------------------------------------------------- LLM engine

class ScriptedProvider:
    """Replays assistant turns; records what it was sent."""

    name = "scripted"

    def __init__(self, turns):
        self.turns = list(turns)
        self.seen = []

    def complete(self, system, messages, tools):
        self.seen.append((system, [dict(m) for m in messages], tools))
        return self.turns.pop(0)


def _llm_service(chat_service, backend, provider):
    return ChatService(chat_service.settings, chat_service.forecasts, SessionStore(None), provider,
                       backend_transport=backend.transport())


def test_llm_tool_loop_executes_tools_and_returns_the_final_text(chat_service, caller, backend):
    provider = ScriptedProvider([
        AssistantTurn("", [ToolRequest("t1", "find_optimal_seats", {"is_quiet_zone": True, "has_power_outlet": True})]),
        AssistantTurn("", [ToolRequest("t2", "reserve_seat", {"seat_code": "A-03"})]),
        AssistantTurn("Đã đặt ghế A-03 cho bạn, hãy quét mã QR trên bàn để check-in."),
    ])
    resp = _llm_service(chat_service, backend, provider).chat(caller, "Tìm và đặt ghế yên tĩnh có ổ cắm")
    assert resp["engine"] == "llm:scripted"
    assert tool_names(resp) == ["find_optimal_seats", "reserve_seat"]
    assert resp["reservation"]["seatCode"] == "A-03" and resp["suggestedSeats"][0]["seatCode"] == "A-03"
    assert resp["reply"].startswith("Đã đặt ghế A-03")
    system, messages, tools = provider.seen[-1]
    assert "Vietnamese" in system and {t["name"] for t in tools} == {"find_optimal_seats", "get_occupancy_forecast",
                                                                       "reserve_seat"}
    assert [m["role"] for m in messages] == ["user", "assistant", "tool", "assistant", "tool"]


def test_llm_failure_falls_back_to_the_deterministic_engine(chat_service, caller, backend):
    class Broken:
        name = "broken"

        def complete(self, *a):
            raise RuntimeError("401 invalid api key")

    resp = _llm_service(chat_service, backend, Broken()).chat(caller, "Đặt giúp mình ghế A-05")
    assert resp["engine"] == "deterministic (fallback from broken)"
    assert resp["reservation"]["seatCode"] == "A-05"


def test_llm_failure_after_booking_does_not_book_twice(chat_service, caller, backend):
    class FailsAfterBooking:
        name = "flaky"
        calls = 0

        def complete(self, system, messages, tools):
            self.calls += 1
            if self.calls == 1:
                return AssistantTurn("", [ToolRequest("t1", "reserve_seat", {"seat_code": "A-02"})])
            raise TimeoutError("read timeout")

    resp = _llm_service(chat_service, backend, FailsAfterBooking()).chat(caller, "book seat A-02")
    assert tool_names(resp) == ["reserve_seat"]  # exactly one booking attempt
    assert resp["reservation"]["seatCode"] == "A-02" and "A-02" in resp["reply"]


def test_anthropic_message_conversion_groups_tool_results():
    msgs = [
        {"role": "user", "content": "hi"},
        {"role": "assistant", "content": "", "tool_calls": [ToolRequest("a", "find_optimal_seats", {}),
                                                            ToolRequest("b", "get_occupancy_forecast", {})]},
        {"role": "tool", "tool_call_id": "a", "name": "find_optimal_seats", "content": "{}"},
        {"role": "tool", "tool_call_id": "b", "name": "get_occupancy_forecast", "content": "{}"},
    ]
    out = AnthropicProvider.convert(msgs)
    assert [m["role"] for m in out] == ["user", "assistant", "user"]
    assert [b["type"] for b in out[1]["content"]] == ["tool_use", "tool_use"]
    assert [b["tool_use_id"] for b in out[2]["content"]] == ["a", "b"]


def test_anthropic_adapter_parses_tool_use_blocks():
    fake_response = SimpleNamespace(content=[
        SimpleNamespace(type="text", text="Let me check."),
        SimpleNamespace(type="tool_use", id="tu_1", name="reserve_seat", input={"seat_code": "A-05"}),
    ])
    client = SimpleNamespace(messages=SimpleNamespace(create=lambda **kw: fake_response))
    turn = AnthropicProvider("key", "claude-sonnet-5-5", 10, client=client).complete("sys", [{"role": "user", "content": "x"}], [])
    assert turn.text == "Let me check." and turn.tool_calls[0].arguments == {"seat_code": "A-05"}


def test_openai_compatible_adapter_round_trip():
    tc = SimpleNamespace(id="c1", function=SimpleNamespace(name="find_optimal_seats", arguments='{"zone_code": "B"}'))
    resp = SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=None, tool_calls=[tc]))])
    sent = {}

    def create(**kw):
        sent.update(kw)
        return resp

    client = SimpleNamespace(chat=SimpleNamespace(completions=SimpleNamespace(create=create)))
    p = OpenAICompatProvider("gemini", "k", "gemini-2.0-flash", None, 10, client=client)
    turn = p.complete("sys", [{"role": "user", "content": "x"}], [{"name": "find_optimal_seats", "description": "d",
                                                                   "parameters": {"type": "object"}}])
    assert turn.tool_calls[0].arguments == {"zone_code": "B"}
    assert sent["messages"][0] == {"role": "system", "content": "sys"}
    assert sent["tools"][0]["type"] == "function"
