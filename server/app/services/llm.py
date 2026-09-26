"""Sentence completion and question understanding with a local LLM served by Ollama."""
from __future__ import annotations

import json
from typing import Protocol

import httpx

from ..logging_utils import log, redact
from ..text import clean_sentence, normalize
from . import rules

SYSTEM_PROMPT = (
    "تو به بیماری کمک می‌کنی که نمی‌تواند حرف بزند یا دستش را حرکت دهد و فقط با پلک زدن روی تبلت فارسی می‌نویسد. "
    "هر کلمه برایش زحمت زیادی دارد؛ پس حدس بزن دقیقاً چه می‌خواهد بگوید. "
    "جمله‌ها باید کوتاه (حداکثر هشت کلمه)، محاوره‌ای، اول‌شخص و از زبان خود بیمار خطاب به خانواده یا پرستار باشند. "
    "اگر بیمار چیزی تایپ کرده، هر جمله باید با همان شروع شود یا آن را به‌طور طبیعی کامل کند. "
    "از نیم‌فاصله‌ی درست استفاده کن (مثل «می‌خوام»). هیچ توضیحی نده."
)

COMPLETE_SCHEMA = {
    "type": "object",
    "properties": {"sentences": {"type": "array", "items": {"type": "string"}}},
    "required": ["sentences"],
}

CLASSIFY_SCHEMA = {
    "type": "object",
    "properties": {
        "type": {"type": "string", "enum": ["yes_no", "choice", "open"]},
        "topic": {"type": "string", "enum": ["needs", "pain", "feelings", "people", "none"]},
        "confidence": {"type": "number"},
    },
    "required": ["type", "topic", "confidence"],
}

CLASSIFY_PROMPT = (
    "همراه بیمار این سؤال را از بیماری پرسیده که فقط با پلک جواب می‌دهد. نوع سؤال را مشخص کن: "
    "yes_no اگر با بله/نه جواب داده می‌شود، choice اگر جوابش یکی از چند گزینه است، open در غیر این صورت. "
    "موضوع: needs (غذا، آب، دستشویی، جابه‌جایی، دما، خواب و نیازهای جسمی)، pain (درد یا علامت بدنی)، "
    "feelings (احساس و حال روحی)، people (افراد، ملاقات، صدا کردن کسی) یا none. "
    "confidence عددی بین ۰ و ۱ است.\nسؤال: «{q}»"
)


def time_of_day(hour: int) -> str:
    if 5 <= hour < 12:
        return "صبح"
    if 12 <= hour < 15:
        return "ظهر"
    if 15 <= hour < 19:
        return "عصر"
    return "شب"


def build_complete_prompt(typed: str, recent: list[str], hour: int, examples: list[str]) -> str:
    parts = [f"زمان: ساعت {hour} ({time_of_day(hour)})"]
    if examples:
        parts.append("جمله‌هایی که این بیمار قبلاً گفته (برای شناخت سبک و نیازهایش):\n" + "\n".join(f"- {e}" for e in examples))
    if recent:
        parts.append("پیام‌های اخیر بیمار (قدیمی به جدید):\n" + "\n".join(f"- {r}" for r in recent[-5:]))
    parts.append(f"متن تایپ‌شده تا الان: «{typed}»" if typed.strip() else "بیمار هنوز چیزی تایپ نکرده؛ محتمل‌ترین جمله‌ها را پیشنهاد بده.")
    parts.append("سه جمله‌ی متفاوت پیشنهاد بده.")
    return "\n\n".join(parts)


class ChatBackend(Protocol):
    async def chat_json(self, system: str, user: str, schema: dict, max_tokens: int) -> dict: ...
    async def available(self) -> bool: ...


class OllamaBackend:
    def __init__(self, url: str, model: str, timeout: float = 10.0):
        self.url = url.rstrip("/")
        self.model = model
        self._client = httpx.AsyncClient(timeout=timeout)

    async def chat_json(self, system: str, user: str, schema: dict, max_tokens: int) -> dict:
        r = await self._client.post(
            f"{self.url}/api/chat",
            json={
                "model": self.model,
                "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
                "stream": False,
                "format": schema,
                "think": False,
                "keep_alive": "60m",
                "options": {"temperature": 0.6, "num_predict": max_tokens},
            },
        )
        r.raise_for_status()
        return json.loads(r.json()["message"]["content"])

    async def available(self) -> bool:
        try:
            r = await self._client.get(f"{self.url}/api/tags", timeout=2.0)
            names = {m.get("name") for m in r.json().get("models", [])}
            return self.model in names or f"{self.model}:latest" in names
        except Exception:
            return False


class LanguageService:
    def __init__(self, backend: ChatBackend, model_name: str):
        self.backend = backend
        self.model_name = model_name

    async def complete(self, typed: str, recent: list[str], hour: int, examples: list[str], n: int = 3) -> list[str]:
        prompt = build_complete_prompt(typed, recent, hour, examples)
        data = await self.backend.chat_json(SYSTEM_PROMPT, prompt, COMPLETE_SCHEMA, 200)
        out: list[str] = []
        prefix = normalize(typed)
        for s in data.get("sentences", []):
            c = clean_sentence(str(s))
            if c and c not in out and c != prefix:
                out.append(c)
        log.info("complete typed=%s -> %d suggestions", redact(typed), len(out))
        return out[:n]

    async def classify(self, text: str) -> tuple[str, str | None, float]:
        try:
            d = await self.backend.chat_json("", CLASSIFY_PROMPT.format(q=text), CLASSIFY_SCHEMA, 60)
            kind = d.get("type") if d.get("type") in ("yes_no", "choice", "open") else "open"
            topic = d.get("topic") if d.get("topic") in ("needs", "pain", "feelings", "people") else None
            conf = float(d.get("confidence", 0.5))
            # Blend with the rules: if both agree we are more confident.
            r_kind, r_topic, _ = rules.classify(text)
            if r_kind == kind:
                conf = min(1.0, conf + 0.1)
            return kind, topic or r_topic, max(0.0, min(1.0, conf))
        except Exception as e:  # model missing or malformed output: never fail the request
            log.warning("classify fell back to rules: %s", type(e).__name__)
            return rules.classify(text)
