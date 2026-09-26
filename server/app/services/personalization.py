"""Stores the patient's own sentences (only if the family allowed syncing) and
retrieves similar ones for few-shot prompting. Pure Python, no external calls."""
from __future__ import annotations

import math
import sqlite3
import threading
from collections import Counter
from pathlib import Path

from ..text import key, normalize


def _grams(text: str, n: int = 3) -> Counter:
    k = f" {key(text)} "
    return Counter(k[i:i + n] for i in range(max(1, len(k) - n + 1)))


class PersonalStore:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self._db = sqlite3.connect(str(path), check_same_thread=False)
        self._lock = threading.Lock()
        with self._lock:
            self._db.execute(
                "CREATE TABLE IF NOT EXISTS sentences (k TEXT PRIMARY KEY, text TEXT NOT NULL, count INTEGER NOT NULL, last_ts INTEGER NOT NULL)"
            )
            self._db.execute("CREATE TABLE IF NOT EXISTS events (ts INTEGER NOT NULL, k TEXT NOT NULL)")
            self._db.commit()

    def add(self, text: str, ts: int) -> bool:
        t = normalize(text)
        if not t:
            return False
        k = key(t)
        with self._lock:
            self._db.execute(
                "INSERT INTO sentences(k, text, count, last_ts) VALUES(?,?,1,?) "
                "ON CONFLICT(k) DO UPDATE SET count = count + 1, last_ts = MAX(last_ts, excluded.last_ts), text = excluded.text",
                (k, t, ts),
            )
            self._db.execute("INSERT INTO events(ts, k) VALUES(?,?)", (ts, k))
            self._db.commit()
        return True

    def count(self) -> int:
        with self._lock:
            return self._db.execute("SELECT COUNT(*) FROM sentences").fetchone()[0]

    def all(self) -> list[tuple[str, int, int]]:
        with self._lock:
            return list(self._db.execute("SELECT text, count, last_ts FROM sentences"))

    def frequent(self, limit: int = 5) -> list[str]:
        with self._lock:
            return [r[0] for r in self._db.execute("SELECT text FROM sentences ORDER BY count DESC, last_ts DESC LIMIT ?", (limit,))]

    def sequence(self) -> list[tuple[int, str]]:
        """All spoken events in time order, for building fine-tuning pairs."""
        with self._lock:
            rows = list(self._db.execute(
                "SELECT e.ts, s.text FROM events e JOIN sentences s ON s.k = e.k ORDER BY e.ts"))
        return [(r[0], r[1]) for r in rows]

    def similar(self, query: str, limit: int = 5) -> list[str]:
        """Character-trigram cosine similarity; good enough for short Persian sentences."""
        if not query.strip():
            return self.frequent(limit)
        q = _grams(query)
        qn = math.sqrt(sum(v * v for v in q.values()))
        scored = []
        for text, count, _ in self.all():
            g = _grams(text)
            dot = sum(v * g.get(k, 0) for k, v in q.items())
            if dot == 0:
                continue
            sim = dot / (qn * math.sqrt(sum(v * v for v in g.values())))
            scored.append((sim + 0.02 * math.log1p(count), text))
        scored.sort(reverse=True)
        return [t for _, t in scored[:limit]]
