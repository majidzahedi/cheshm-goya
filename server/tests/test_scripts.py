import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    sys.modules[name] = mod  # dataclasses need the module registered
    spec.loader.exec_module(mod)
    return mod


def test_hardware_tiers():
    hw = load("detect_hardware")
    assert hw.plan_for(48).llm == "qwen3.5:27b"
    assert hw.plan_for(24).llm == "qwen3.5:9b"
    assert hw.plan_for(10).whisper_compute == "int8_float16"
    assert hw.plan_for(0).tier == "cpu"


def test_finetune_examples_use_previous_messages():
    ft = load("finetune_lora")
    seq = [(1_000, "سلام"), (2_000, "آب می‌خوام"), (3_000, "ممنونم")]
    ex = ft.build_examples(seq)
    assert len(ex) == 3
    prefix, target = ex[2]
    assert "سلام" in prefix and "آب می‌خوام" in prefix and target == " ممنونم"
