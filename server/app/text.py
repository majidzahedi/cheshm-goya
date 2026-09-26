"""Persian normalisation, mirroring app/core PersianText."""
from __future__ import annotations

import re

ZWNJ = "‌"
_DIACRITICS = re.compile("[ً-ٰٟـ]")
_SPACES = re.compile(r"\s+")
_TRANS = str.maketrans({"ي": "ی", "ى": "ی", "ك": "ک", **{chr(0x660 + i): chr(0x6F0 + i) for i in range(10)}})


def normalize(text: str) -> str:
    return _SPACES.sub(" ", text.translate(_TRANS).replace("ـ", "")).strip()


def key(text: str) -> str:
    t = _DIACRITICS.sub("", normalize(text)).replace(ZWNJ, "")
    return t.replace("ۀ", "ه").replace("ة", "ه").replace("أ", "ا").replace("إ", "ا").replace("ؤ", "و").lower()


def clean_sentence(text: str, max_words: int = 12) -> str:
    """Tidy one generated suggestion: strip numbering, quotes and trailing junk."""
    t = normalize(text)
    t = re.sub(r"^[\s\-•*\d۰-۹.)]+", "", t)
    t = t.strip(" \"'«»“”")
    words = t.split(" ")
    return " ".join(words[:max_words]).strip()
