"""LLM function-calling engine.

One provider-agnostic tool loop + thin adapters:
  * AnthropicProvider     - Claude Messages API (tool use)
  * OpenAICompatProvider  - OpenAI, Gemini (OpenAI-compatible endpoint) and Ollama (/v1)
Any provider error is raised to ChatService, which falls back to the deterministic engine.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Protocol

from app.agent.deterministic import ChatResult
from app.agent.text import detect_language
from app.agent.tools import TOOL_SPECS, ToolExecutor
from app.config import Settings

log = logging.getLogger(__name__)
MAX_ROUNDS = 5


@dataclass
class ToolRequest:
    id: str
    name: str
    arguments: dict[str, Any]


@dataclass
class AssistantTurn:
    text: str
    tool_calls: list[ToolRequest] = field(default_factory=list)


# Internal message format:
#   {"role": "user", "content": str}
#   {"role": "assistant", "content": str, "tool_calls": [ToolRequest, ...]}
#   {"role": "tool", "tool_call_id": str, "name": str, "content": str(json)}

class LLMProvider(Protocol):
    name: str

    def complete(self, system: str, messages: list[dict[str, Any]], tools: list[dict[str, Any]]) -> AssistantTurn: ...


SYSTEM_PROMPT = """You are the study-space assistant of the VNU-IS library (Smart Self-Study Space platform).
Current local time: {now} (Asia/Ho_Chi_Minh). Zones: A = quiet zone, B = general, C = group tables; seats A-01..C-20.

Rules:
- Always answer in the student's language ({language_name}); be brief and friendly.
- Use the tools for every fact about seats, occupancy or bookings; never invent seat codes or numbers.
- Only call reserve_seat when the student clearly asked to book (e.g. "đặt", "book", "giữ chỗ").
  If they only asked to find seats, list the options and ask which one to book.
- If reserve_seat fails because the seat was taken (SEAT_NOT_AVAILABLE / SEAT_BUSY), try the next suggested seat.
- After a successful booking, give the seat code, how long it is held (secondsUntilExpiry) and the check-in steps:
  go to the seat, sit down, scan the QR code on the desk; the seat sensor must detect the student.
  Mention that a no-show releases the seat and costs 10 credit points.
- Occupancy is a percentage; above 75 % is busy, below 40 % is quiet.
"""


def system_prompt(now: datetime, language: str) -> str:
    return SYSTEM_PROMPT.format(now=now.strftime("%A %Y-%m-%d %H:%M"),
                                language_name="Vietnamese" if language == "vi" else "English")


# ---------------------------------------------------------------------------- adapters

class AnthropicProvider:
    name = "anthropic"

    def __init__(self, api_key: str, model: str, timeout: float, client: Any = None):
        if client is None:
            import anthropic
            client = anthropic.Anthropic(api_key=api_key, timeout=timeout, max_retries=1)
        self.client = client
        self.model = model

    @staticmethod
    def convert(messages: list[dict[str, Any]]) -> list[dict[str, Any]]:
        out: list[dict[str, Any]] = []
        for m in messages:
            if m["role"] == "user":
                out.append({"role": "user", "content": m["content"]})
            elif m["role"] == "assistant":
                blocks: list[dict[str, Any]] = []
                if m.get("content"):
                    blocks.append({"type": "text", "text": m["content"]})
                for tc in m.get("tool_calls", []):
                    blocks.append({"type": "tool_use", "id": tc.id, "name": tc.name, "input": tc.arguments})
                out.append({"role": "assistant", "content": blocks or m.get("content", "")})
            elif m["role"] == "tool":
                block = {"type": "tool_result", "tool_use_id": m["tool_call_id"], "content": m["content"]}
                # Consecutive tool results go into one user turn.
                if out and out[-1]["role"] == "user" and isinstance(out[-1]["content"], list) \
                        and out[-1]["content"] and out[-1]["content"][0].get("type") == "tool_result":
                    out[-1]["content"].append(block)
                else:
                    out.append({"role": "user", "content": [block]})
        return out

    def complete(self, system: str, messages: list[dict[str, Any]], tools: list[dict[str, Any]]) -> AssistantTurn:
        resp = self.client.messages.create(
            model=self.model, max_tokens=1024, system=system, messages=self.convert(messages),
            tools=[{"name": t["name"], "description": t["description"], "input_schema": t["parameters"]} for t in tools],
        )
        text = "".join(b.text for b in resp.content if b.type == "text")
        calls = [ToolRequest(b.id, b.name, dict(b.input or {})) for b in resp.content if b.type == "tool_use"]
        return AssistantTurn(text=text, tool_calls=calls)


class OpenAICompatProvider:
    def __init__(self, name: str, api_key: str, model: str, base_url: str | None, timeout: float, client: Any = None):
        if client is None:
            import openai
            client = openai.OpenAI(api_key=api_key, base_url=base_url, timeout=timeout, max_retries=1)
        self.name = name
        self.client = client
        self.model = model

    @staticmethod
    def convert(system: str, messages: list[dict[str, Any]]) -> list[dict[str, Any]]:
        out: list[dict[str, Any]] = [{"role": "system", "content": system}]
        for m in messages:
            if m["role"] == "assistant" and m.get("tool_calls"):
                out.append({"role": "assistant", "content": m.get("content") or None, "tool_calls": [
                    {"id": tc.id, "type": "function",
                     "function": {"name": tc.name, "arguments": json.dumps(tc.arguments, ensure_ascii=False)}}
                    for tc in m["tool_calls"]]})
            elif m["role"] == "tool":
                out.append({"role": "tool", "tool_call_id": m["tool_call_id"], "content": m["content"]})
            else:
                out.append({"role": m["role"], "content": m["content"]})
        return out

    def complete(self, system: str, messages: list[dict[str, Any]], tools: list[dict[str, Any]]) -> AssistantTurn:
        resp = self.client.chat.completions.create(
            model=self.model, messages=self.convert(system, messages), tool_choice="auto",
            tools=[{"type": "function", "function": t} for t in tools],
        )
        msg = resp.choices[0].message
        calls = []
        for tc in msg.tool_calls or []:
            try:
                args = json.loads(tc.function.arguments or "{}")
            except json.JSONDecodeError:
                args = {}
            calls.append(ToolRequest(tc.id, tc.function.name, args))
        return AssistantTurn(text=msg.content or "", tool_calls=calls)


def make_provider(settings: Settings) -> LLMProvider | None:
    """The configured provider, or None for the deterministic engine."""
    choice = settings.llm_provider.lower()
    t = settings.llm_timeout_seconds
    if choice == "deterministic":
        return None
    if choice in ("auto", "anthropic") and settings.anthropic_api_key:
        return AnthropicProvider(settings.anthropic_api_key, settings.anthropic_model, t)
    if choice in ("auto", "openai") and settings.openai_api_key:
        return OpenAICompatProvider("openai", settings.openai_api_key, settings.openai_model, settings.openai_base_url, t)
    if choice in ("auto", "gemini") and settings.gemini_api_key:
        return OpenAICompatProvider("gemini", settings.gemini_api_key, settings.gemini_model, settings.gemini_base_url, t)
    if choice in ("auto", "ollama") and settings.ollama_base_url:
        return OpenAICompatProvider("ollama", "ollama", settings.ollama_model, settings.ollama_base_url, t)
    if choice != "auto":
        log.warning("LLM_PROVIDER=%s but its key/URL is not set; using the deterministic engine", choice)
    return None


# ---------------------------------------------------------------------------- loop

class LLMAgent:
    def __init__(self, provider: LLMProvider):
        self.provider = provider

    def respond(self, message: str, executor: ToolExecutor, history: list[dict[str, str]], now: datetime) -> ChatResult:
        language = detect_language(message)
        messages: list[dict[str, Any]] = [*history, {"role": "user", "content": message}]
        system = system_prompt(now, language)
        for _ in range(MAX_ROUNDS):
            turn = self.provider.complete(system, messages, TOOL_SPECS)
            if not turn.tool_calls:
                return self._result(turn.text, language, executor)
            messages.append({"role": "assistant", "content": turn.text, "tool_calls": turn.tool_calls})
            for req in turn.tool_calls:
                call = executor.call(req.name, req.arguments)
                messages.append({"role": "tool", "tool_call_id": req.id, "name": req.name,
                                 "content": json.dumps(call.as_dict(), ensure_ascii=False, default=str)[:6000]})
        # Out of rounds: ask for a final answer without tools.
        turn = self.provider.complete(system, messages + [
            {"role": "user", "content": "Summarise the result for the student now, without calling more tools."}], [])
        return self._result(turn.text, language, executor)

    def _result(self, text: str, language: str, executor: ToolExecutor) -> ChatResult:
        suggested = next((c.result for c in reversed(executor.trace) if c.ok and c.name == "find_optimal_seats"), None)
        reservation = next((c.result for c in reversed(executor.trace) if c.ok and c.name == "reserve_seat"), None)
        return ChatResult(reply=text.strip(), language=language, engine=f"llm:{self.provider.name}",
                          tool_calls=list(executor.trace), suggested_seats=suggested, reservation=reservation)
