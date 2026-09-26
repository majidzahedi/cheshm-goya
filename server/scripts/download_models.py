#!/usr/bin/env python3
"""One-time download of the models into ./models (the only step that needs internet).

After this, the server runs fully offline (HF_HUB_OFFLINE=1 in docker-compose).

    python3 scripts/download_models.py            # uses the models named in .env
    docker compose exec ollama ollama pull <CG_LLM_MODEL>
"""
from __future__ import annotations

import os
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / "models"
os.environ.setdefault("HF_HOME", str(MODELS / "hf"))
sys.path.insert(0, str(ROOT))

PIPER_BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/main/fa/fa_IR"
PIPER_VOICES = ["amir/medium", "gyro/medium", "reza_ibrahim/medium", "ganji/medium"]
WHISPER_REPOS = {
    "large-v3-turbo": "mobiuslabsgmbh/faster-whisper-large-v3-turbo",
    "large-v3": "Systran/faster-whisper-large-v3",
    "medium": "Systran/faster-whisper-medium",
    "small": "Systran/faster-whisper-small",
}


def env(name: str, default: str) -> str:
    f = ROOT / ".env"
    if f.exists():
        for line in f.read_text(encoding="utf-8").splitlines():
            if line.startswith(name + "="):
                return line.split("=", 1)[1].strip() or default
    return os.environ.get(name, default)


def main() -> None:
    from huggingface_hub import snapshot_download

    ranker = env("CG_RANKER_MODEL", "Qwen/Qwen3-1.7B")
    whisper = env("CG_WHISPER_MODEL", "large-v3-turbo")

    print(f"→ ranker {ranker}")
    snapshot_download(ranker, allow_patterns=["*.json", "*.safetensors", "*.txt", "*.model", "tokenizer*"])

    repo = WHISPER_REPOS.get(whisper, whisper)
    print(f"→ whisper {repo}")
    snapshot_download(repo)

    piper_dir = MODELS / "piper"
    piper_dir.mkdir(parents=True, exist_ok=True)
    for v in PIPER_VOICES:
        speaker, quality = v.split("/")
        name = f"fa_IR-{speaker}-{quality}"
        for ext in (".onnx", ".onnx.json"):
            target = piper_dir / f"{name}{ext}"
            if not target.exists():
                print(f"→ piper {target.name}")
                try:
                    urllib.request.urlretrieve(f"{PIPER_BASE}/{v}/{name}{ext}", target)
                except Exception as e:
                    print(f"   skipped ({e})")
    llm = env("CG_LLM_MODEL", "qwen3.5:9b")
    print(f"\nDone. Now pull the language model into Ollama:\n  docker compose up -d ollama && docker compose exec ollama ollama pull {llm}")


if __name__ == "__main__":
    main()
