"""Persian speech-to-text with faster-whisper, loaded lazily."""
from __future__ import annotations

import io
import threading

from ..logging_utils import log, redact


class WhisperSTT:
    def __init__(self, model: str, compute_type: str = "float16"):
        self.model_name = model
        self.compute_type = compute_type
        self._model = None
        self._lock = threading.Lock()

    @property
    def loaded(self) -> bool:
        return self._model is not None

    def _load(self):
        with self._lock:
            if self._model is None:
                from faster_whisper import WhisperModel

                try:
                    self._model = WhisperModel(self.model_name, device="cuda", compute_type=self.compute_type)
                except Exception as e:  # no GPU: fall back to CPU int8
                    log.warning("whisper on CUDA failed (%s); using CPU", type(e).__name__)
                    self._model = WhisperModel(self.model_name, device="cpu", compute_type="int8")
                log.info("whisper %s loaded", self.model_name)

    def transcribe(self, audio: bytes, language: str = "fa") -> str:
        self._load()
        segments, _ = self._model.transcribe(
            io.BytesIO(audio), language=language, beam_size=5, vad_filter=True,
            initial_prompt="سؤال همراه بیمار از بیمار، به فارسی محاوره‌ای.",
        )
        text = " ".join(s.text.strip() for s in segments).strip()
        log.info("stt -> %s", redact(text))
        return text
