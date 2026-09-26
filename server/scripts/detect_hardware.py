#!/usr/bin/env python3
"""Inspect this computer (GPU, VRAM, RAM, OS) and choose models that fit.

Usage:  python3 scripts/detect_hardware.py            # print a report
        python3 scripts/detect_hardware.py --write-env # also write .env

Model choices were researched in September 2026 (see server/README.md).
Latency matters more than size: sentence suggestions must arrive in < 3 s.
"""
from __future__ import annotations

import argparse
import os
import platform
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path


@dataclass
class Gpu:
    name: str
    vram_gb: float
    driver: str


@dataclass
class Plan:
    tier: str
    llm: str
    ranker: str
    whisper: str
    whisper_compute: str
    notes: str


def detect_gpus() -> list[Gpu]:
    if not shutil.which("nvidia-smi"):
        return []
    try:
        out = subprocess.run(
            ["nvidia-smi", "--query-gpu=name,memory.total,driver_version", "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=15, check=True,
        ).stdout
    except Exception:
        return []
    gpus = []
    for line in out.strip().splitlines():
        name, mem, driver = [x.strip() for x in line.split(",")]
        gpus.append(Gpu(name, float(mem) / 1024, driver))
    return gpus


def ram_gb() -> float:
    try:
        return os.sysconf("SC_PAGE_SIZE") * os.sysconf("SC_PHYS_PAGES") / 1024**3
    except (ValueError, OSError, AttributeError):
        return 0.0


def plan_for(vram: float) -> Plan:
    if vram >= 32:
        return Plan("large", "qwen3.5:27b", "Qwen/Qwen3-4B", "large-v3", "float16",
                    "مدل ۲۷ میلیاردی با کیفیت فارسی بالا؛ هنوز زیر ۳ ثانیه جواب می‌دهد.")
    if vram >= 12:
        return Plan("medium", "qwen3.5:9b", "Qwen/Qwen3-1.7B", "large-v3-turbo", "float16",
                    "بهترین تعادل سرعت و کیفیت برای کارت‌های ۱۲ تا ۳۲ گیگابایتی.")
    if vram >= 8:
        return Plan("small", "qwen3.5:4b", "Qwen/Qwen3-1.7B", "large-v3-turbo", "int8_float16",
                    "مدل ۴ میلیاردی؛ جمله‌ها ساده‌ترند ولی سریع.")
    if vram >= 5:
        return Plan("tiny", "qwen3.5:2b", "Qwen/Qwen3-0.6B", "large-v3-turbo", "int8_float16",
                    "حافظه‌ی گرافیک کم است؛ مدل‌های کوچک انتخاب شد.")
    return Plan("cpu", "qwen3.5:2b", "Qwen/Qwen3-0.6B", "small", "int8",
                "کارت گرافیک NVIDIA پیدا نشد؛ روی CPU کار می‌کند ولی کند است. برای این پروژه GPU توصیه می‌شود.")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--write-env", action="store_true", help="write the chosen models to .env")
    args = ap.parse_args()

    gpus = detect_gpus()
    vram = max((g.vram_gb for g in gpus), default=0.0)
    p = plan_for(vram)
    print("=== Cheshm-Goya hardware check ===")
    print(f"OS:   {platform.system()} {platform.release()} ({platform.machine()})")
    print(f"RAM:  {ram_gb():.1f} GB")
    print(f"Disk free here: {shutil.disk_usage('.').free / 1024**3:.0f} GB")
    if gpus:
        for g in gpus:
            print(f"GPU:  {g.name}  {g.vram_gb:.1f} GB VRAM  (driver {g.driver})")
    else:
        print("GPU:  no NVIDIA GPU detected (nvidia-smi not found or failed)")
    print(f"\nTier: {p.tier}")
    print(f"  LLM (Ollama):   {p.llm}")
    print(f"  Ranker (HF):    {p.ranker}")
    print(f"  Whisper:        {p.whisper} ({p.whisper_compute})")
    print("  TTS:            Piper fa_IR voices (CPU)")
    print(f"  {p.notes}")
    if platform.system() == "Windows":
        print("\nWindows: run inside WSL2 with Docker Engine and the NVIDIA container toolkit, "
              "or run natively with `python -m app.main` (mDNS needs the host network).")

    if args.write_env:
        env = Path(".env")
        example = Path(".env.example").read_text(encoding="utf-8") if Path(".env.example").exists() else ""
        values = {
            "CG_LLM_MODEL": p.llm, "CG_RANKER_MODEL": p.ranker,
            "CG_WHISPER_MODEL": p.whisper, "CG_WHISPER_COMPUTE_TYPE": p.whisper_compute,
        }
        lines = []
        for line in example.splitlines():
            k = line.split("=", 1)[0]
            lines.append(f"{k}={values.pop(k)}" if k in values else line)
        lines += [f"{k}={v}" for k, v in values.items()]
        if env.exists():
            env.rename(".env.bak")
            print("\n(existing .env saved as .env.bak)")
        env.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"\nWrote {env.resolve()}")


if __name__ == "__main__":
    main()
