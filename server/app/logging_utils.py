"""Logging that never records what the patient said unless explicitly allowed."""
from __future__ import annotations

import logging

from .config import settings

log = logging.getLogger("cheshmgoya")


def configure_logging() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


def redact(text: str | None) -> str:
    """Return the text itself only when CG_LOG_MESSAGE_TEXT=true; otherwise just its length."""
    if text is None:
        return "<none>"
    if settings.log_message_text:
        return repr(text)
    return f"<{len(text)} chars>"
