"""Voice banking: store recordings of the patient's own voice.

Honest status (September 2026): I could not verify any open-source voice-cloning
model whose *Persian* output is good enough for a patient's personal voice, so no
cloning backend is enabled by default. Recordings are still valuable — they are
kept safely on this computer (never uploaded) so a personal voice can be built
later when a suitable model is chosen and tested. A backend can be plugged in
through the `CloneBackend` protocol and CG_VOICE_CLONE_BACKEND.
"""
from __future__ import annotations

import io
import json
import re
import time
import wave
from pathlib import Path
from typing import Protocol

from ..logging_utils import log

MIN_TOTAL_SECONDS = 30.0
_SAFE = re.compile(r"[^a-zA-Z0-9_-]+")


class CloneBackend(Protocol):
    name: str

    def build(self, voice_dir: Path) -> None: ...
    def synthesize(self, voice_dir: Path, text: str) -> bytes: ...


def wav_seconds(data: bytes) -> float | None:
    try:
        with wave.open(io.BytesIO(data)) as w:
            return w.getnframes() / float(w.getframerate())
    except Exception:
        return None


class VoiceBank:
    def __init__(self, root: Path, backend: CloneBackend | None = None):
        self.root = root
        self.backend = backend

    @property
    def cloning_available(self) -> bool:
        return self.backend is not None

    def voice_dir(self, voice_id: str) -> Path:
        return self.root / _SAFE.sub("_", voice_id)

    def enroll(self, name: str, files: list[tuple[str, bytes]]) -> dict:
        voice_id = "personal-" + (_SAFE.sub("_", name).strip("_") or "patient")
        d = self.voice_dir(voice_id)
        (d / "samples").mkdir(parents=True, exist_ok=True)
        stored, seconds = 0, 0.0
        for filename, data in files:
            ext = Path(filename).suffix.lower() or ".bin"
            if ext not in (".wav", ".mp3", ".m4a", ".ogg", ".flac", ".opus"):
                continue
            (d / "samples" / f"{int(time.time() * 1000)}_{stored}{ext}").write_bytes(data)
            stored += 1
            seconds += wav_seconds(data) or 0.0
        meta_path = d / "meta.json"
        meta = json.loads(meta_path.read_text()) if meta_path.exists() else {"name": name, "seconds": 0.0, "files": 0}
        meta["seconds"] = meta.get("seconds", 0.0) + seconds
        meta["files"] = meta.get("files", 0) + stored
        meta_path.write_text(json.dumps(meta, ensure_ascii=False))
        ready = False
        if self.backend and meta["seconds"] >= MIN_TOTAL_SECONDS:
            try:
                self.backend.build(d)
                (d / "ready").touch()
                ready = True
            except Exception as e:
                log.warning("voice build failed: %s", type(e).__name__)
        if ready:
            msg = "صدای شخصی ساخته شد."
        elif self.backend:
            msg = f"ضبط‌ها ذخیره شد. برای ساخت صدا دست‌کم {int(MIN_TOTAL_SECONDS)} ثانیه صدای واضح لازم است."
        else:
            msg = ("ضبط‌ها با امنیت روی همین کامپیوتر ذخیره شد (بانک صدا). "
                   "فعلاً مدل شبیه‌سازی صدای فارسیِ با کیفیت قابل قبول فعال نیست؛ تا آن زمان صدای معمولی پخش می‌شود.")
        return {"voice_id": voice_id, "files_stored": stored, "total_seconds": round(meta["seconds"], 1), "cloning_available": ready, "message": msg}

    def personal_voices(self) -> list[tuple[str, str]]:
        if not self.root.exists():
            return []
        out = []
        for d in sorted(self.root.iterdir()):
            meta = d / "meta.json"
            if meta.exists() and (d / "ready").exists():
                out.append((d.name, json.loads(meta.read_text()).get("name", d.name)))
        return out

    def synthesize(self, voice_id: str, text: str) -> bytes | None:
        d = self.voice_dir(voice_id)
        if not self.backend or not (d / "ready").exists():
            return None
        return self.backend.synthesize(d, text)
