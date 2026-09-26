"""Rule-based fallback for question classification (mirrors the app's offline classifier)."""
from __future__ import annotations

from ..text import key

TOPICS: dict[str, list[str]] = {
    "pain": ["درد", "میسوز", "اذیت", "کجات", "سرت", "دستت", "پات", "شکمت", "سینه", "نفس", "تهوع", "میخار", "خارش", "گیج"],
    "needs": ["غذا", "گرسن", "تشنه", "آب", "بخوری", "بنوشی", "دستشویی", "جابجا", "پتو", "سرد", "گرم", "بخوابی", "خواب", "ساکشن", "تلویزیون", "چراغ", "بالش", "لباس", "حموم"],
    "feelings": ["حالت", "احساس", "ناراحت", "خوشحال", "میترسی", "نگران", "حوصله", "عصبانی", "دلت", "خسته"],
    "people": ["کی رو", "کسی", "صدا کنم", "زنگ", "خانواده", "پرستار", "دکتر", "ملاقات", "بیاد", "پیشت"],
}
YES_NO_VERBS = ["میخوای", "میخواین", "داری", "دارین", "هست", "هستی", "خوبی", "میتونی", "موافقی", "باشه", "بیارم", "بدم", "کنم", "ببرم", "بذارم"]
OPEN_WORDS = ["چی", "چه", "چرا", "کی"]
# Question words that also appear inflected (چطوره، کجاست، چندتا …).
OPEN_PREFIXES = ["چطور", "چجور", "کجا", "کدوم", "چند"]


def classify(text: str) -> tuple[str, str | None, float]:
    k = key(text)
    compact = k.replace(" ", "")
    words = [w.strip("؟?!.،,") for w in k.split(" ")]

    def has(w: str) -> bool:
        return w in k or w.replace(" ", "") in compact

    topic = next((t for t, ws in TOPICS.items() if any(has(w) for w in ws)), None)
    is_open = any(w in OPEN_WORDS or w.startswith(tuple(OPEN_PREFIXES)) for w in words) or has("کجات")
    is_yes_no = not is_open and (k.startswith("آیا") or k.startswith("ایا") or any(has(v) for v in YES_NO_VERBS) or k.endswith(("؟", "?")))
    if is_open:
        kind = "choice" if topic else "open"
    elif is_yes_no:
        kind = "yes_no"
    else:
        kind = "choice" if topic else "open"
    conf = 0.7 if kind == "yes_no" else (0.6 if topic else 0.3)
    return kind, topic, conf
