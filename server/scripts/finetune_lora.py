#!/usr/bin/env python3
"""Optional: personalise the phrase ranker with LoRA on the patient's own sentences.

Only makes sense once enough sentences were synced (with the family's permission);
until then few-shot retrieval in the prompts does the personalisation.

    pip install -r requirements-finetune.txt
    python3 scripts/finetune_lora.py --min-sentences 300
    # then set CG_RANKER_MODEL=/srv/models/ranker-personal in .env and restart

Training data: for each spoken sentence, the previous messages as context and the
sentence itself as the continuation — exactly what the ranker scores at runtime.
Everything stays on this computer.
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
os.environ.setdefault("HF_HOME", str(ROOT / "models" / "hf"))


def build_examples(seq: list[tuple[int, str]], window_s: int = 6 * 3600) -> list[tuple[str, str]]:
    from app.services.ranker import context_text

    examples = []
    for i, (ts, text) in enumerate(seq):
        recent = [t for (pts, t) in seq[max(0, i - 5):i] if ts - pts <= window_s * 1000]
        hour = (ts // 1000 // 3600) % 24  # UTC hour is close enough for style learning
        prefix = context_text("", recent, int(hour), []) + "\nجمله‌ی بعدی بیمار:"
        examples.append((prefix, " " + text))
    return examples


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=str(ROOT / "data" / "personal.db"))
    ap.add_argument("--base", default=os.environ.get("CG_RANKER_MODEL", "Qwen/Qwen3-1.7B"))
    ap.add_argument("--out", default=str(ROOT / "models" / "ranker-personal"))
    ap.add_argument("--min-sentences", type=int, default=300)
    ap.add_argument("--epochs", type=int, default=2)
    args = ap.parse_args()

    from app.services.personalization import PersonalStore

    store = PersonalStore(Path(args.data))
    seq = store.sequence()
    if len(seq) < args.min_sentences:
        print(f"Only {len(seq)} sentences so far (need {args.min_sentences}). Few-shot retrieval is already personalising; try later.")
        return

    import torch
    from peft import LoraConfig, get_peft_model
    from transformers import AutoModelForCausalLM, AutoTokenizer

    tok = AutoTokenizer.from_pretrained(args.base)
    if tok.pad_token is None:
        tok.pad_token = tok.eos_token
    model = AutoModelForCausalLM.from_pretrained(args.base, torch_dtype=torch.bfloat16).cuda()
    model = get_peft_model(model, LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05, target_modules=["q_proj", "k_proj", "v_proj", "o_proj"], task_type="CAUSAL_LM"))
    model.print_trainable_parameters()

    examples = build_examples(seq)
    opt = torch.optim.AdamW((p for p in model.parameters() if p.requires_grad), lr=2e-4)
    model.train()
    for epoch in range(args.epochs):
        total = 0.0
        for prefix, target in examples:
            p_ids = tok(prefix, add_special_tokens=False).input_ids
            t_ids = tok(target, add_special_tokens=False).input_ids + [tok.eos_token_id]
            ids = torch.tensor([p_ids + t_ids]).cuda()
            labels = ids.clone()
            labels[:, : len(p_ids)] = -100  # learn only the patient's sentence
            loss = model(input_ids=ids, labels=labels).loss
            loss.backward()
            opt.step()
            opt.zero_grad()
            total += loss.item()
        print(f"epoch {epoch + 1}: loss {total / len(examples):.3f}")

    merged = model.merge_and_unload()
    merged.save_pretrained(args.out)
    tok.save_pretrained(args.out)
    print(f"Saved personalised ranker to {args.out}. Set CG_RANKER_MODEL to it and restart the server.")


if __name__ == "__main__":
    main()
