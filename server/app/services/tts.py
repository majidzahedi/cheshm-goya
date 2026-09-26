"""Persian text-to-speech with Piper voices (fully local, runs fine on CPU)."""
from __future__ import annotations

import io
import threading
import wave
from pathlib import Path

from ..logging_utils import log, redact

# Voices from rhasspy/piper-voices that speak Persian (downloaded by scripts/download_models.py).
KNOWN_VOICES = {
    "fa_IR-amir-medium": "امیر (مرد)",
    "fa_IR-gyro-medium": "ژیرو (مرد)",
    "fa_IR-reza_ibrahim-medium": "رضا (مرد)",
    "fa_IR-ganji-medium": "گنجی (مرد)",
    "fa_IR-ganji_adabi-medium": "گنجی ادبی (مرد)",
}


class PiperTTS:
    def __init__(self, voices_dir: Path, default_voice: str):
        self.voices_dir = voices_dir
        self.default_voice = default_voice
        self._voices: dict[str, object] = {}
        self._lock = threading.Lock()

    def available_voices(self) -> list[str]:
        if not self.voices_dir.exists():
            return []
        return sorted(p.stem for p in self.voices_dir.glob("*.onnx") if p.with_suffix(".onnx.json").exists())

    @property
    def loaded(self) -> bool:
        return bool(self.available_voices())

    def _voice(self, voice_id: str):
        with self._lock:
            if voice_id not in self._voices:
                from piper import PiperVoice

                path = self.voices_dir / f"{voice_id}.onnx"
                if not path.exists():
                    raise FileNotFoundError(voice_id)
                self._voices[voice_id] = PiperVoice.load(str(path))
            return self._voices[voice_id]

    def synthesize(self, text: str, voice: str = "default") -> bytes:
        voice_id = self.default_voice if voice in ("", "default") else voice
        if voice_id not in self.available_voices():
            voice_id = self.default_voice
        v = self._voice(voice_id)
        buf = io.BytesIO()
        with wave.open(buf, "wb") as wav:
            if hasattr(v, "synthesize_wav"):  # piper-tts >= 1.3
                v.synthesize_wav(text, wav)
            else:  # older piper-tts
                v.synthesize(text, wav)
        log.info("tts voice=%s text=%s", voice_id, redact(text))
        return buf.getvalue()
