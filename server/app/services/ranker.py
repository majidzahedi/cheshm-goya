"""Phrase ranking the openjev way: probabilities read straight from a small
model's logits, instead of generating text.

Two single-forward-pass steps:
  1. likelihood — every candidate phrase is appended to the same context and
     all of them are scored in one batched forward pass (mean token log-prob);
  2. choice — the best few are listed as lettered options and the next-token
     logits over just those letters give a calibrated distribution (like Jev's
     `Choice`, which softmaxes only over the options supplied at runtime).
"""
from __future__ import annotations

import math
from typing import Protocol

from ..logging_utils import log
from .llm import time_of_day

LETTERS = "ABCDEFGH"


def context_text(typed: str, recent: list[str], hour: int, examples: list[str]) -> str:
    lines = ["بیماری که نمی‌تواند حرف بزند با پلک زدن روی تبلت پیام فارسی انتخاب می‌کند.", f"زمان: {time_of_day(hour)}"]
    if examples:
        lines.append("جمله‌های رایج این بیمار: " + " | ".join(examples))
    if recent:
        lines.append("پیام‌های اخیر: " + " | ".join(recent[-5:]))
    if typed.strip():
        lines.append(f"متن تایپ‌شده: {typed}")
    return "\n".join(lines)


def softmax(xs: list[float], temperature: float = 1.0) -> list[float]:
    m = max(xs)
    es = [math.exp((x - m) / temperature) for x in xs]
    s = sum(es)
    return [e / s for e in es]


class LogitScorer(Protocol):
    name: str

    def sequence_scores(self, prefix: str, continuations: list[str]) -> list[float]:
        """Mean log-probability of each continuation given the prefix (one batched pass)."""

    def choice_distribution(self, prompt: str, labels: list[str]) -> list[float]:
        """Next-token probability of each label, renormalised over the labels (one pass)."""


class HFScorer:
    """Hugging Face causal LM. Loaded lazily so the server starts instantly."""

    def __init__(self, model_id: str, device: str = "auto"):
        self.name = model_id
        self.model_id = model_id
        self.device_pref = device
        self._model = None
        self._tok = None

    def _load(self):
        if self._model is not None:
            return
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer

        dev = self.device_pref
        if dev == "auto":
            dev = "cuda" if torch.cuda.is_available() else "cpu"
        self._tok = AutoTokenizer.from_pretrained(self.model_id)
        if self._tok.pad_token is None:
            self._tok.pad_token = self._tok.eos_token
        dtype = torch.bfloat16 if dev == "cuda" else torch.float32
        self._model = AutoModelForCausalLM.from_pretrained(self.model_id, torch_dtype=dtype).to(dev).eval()
        self._device = dev
        log.info("ranker %s loaded on %s", self.model_id, dev)

    @property
    def loaded(self) -> bool:
        return self._model is not None

    def sequence_scores(self, prefix: str, continuations: list[str], batch: int = 32) -> list[float]:
        import torch

        self._load()
        tok, model = self._tok, self._model
        prefix_ids = tok(prefix, add_special_tokens=False).input_ids
        conts = [tok(" " + c, add_special_tokens=False).input_ids or [tok.eos_token_id] for c in continuations]
        scores: list[float] = []
        for start in range(0, len(conts), batch):
            chunk = conts[start:start + batch]
            seqs = [prefix_ids + c for c in chunk]
            width = max(len(x) for x in seqs)
            keep = max(len(c) for c in chunk) + 1
            # Left padding puts every continuation at the end, so only the last `keep`
            # positions need logits — the full [batch, width, vocab] tensor would be
            # several GB with a 150k-token vocabulary.
            ids = torch.full((len(seqs), width), tok.pad_token_id, dtype=torch.long)
            attn = torch.zeros((len(seqs), width), dtype=torch.long)
            for i, x in enumerate(seqs):
                ids[i, width - len(x):] = torch.tensor(x)
                attn[i, width - len(x):] = 1
            pos = (attn.cumsum(-1) - 1).clamp(min=0)
            ids, attn, pos = ids.to(self._device), attn.to(self._device), pos.to(self._device)
            with torch.inference_mode():
                logits = self._forward_last(model, ids, attn, pos, keep)  # [B, keep, V]
            for i, c in enumerate(chunk):
                n = len(c)
                lp = torch.log_softmax(logits[i, keep - n - 1: keep - 1].float(), dim=-1)
                target = torch.tensor(c, device=lp.device).unsqueeze(-1)
                scores.append(lp.gather(-1, target).mean().item())
        return scores

    @staticmethod
    def _forward_last(model, ids, attn, pos, keep):
        for arg in ("logits_to_keep", "num_logits_to_keep"):
            try:
                return model(input_ids=ids, attention_mask=attn, position_ids=pos, **{arg: keep}).logits[:, -keep:]
            except TypeError:
                continue
        return model(input_ids=ids, attention_mask=attn, position_ids=pos).logits[:, -keep:]

    def choice_distribution(self, prompt: str, labels: list[str]) -> list[float]:
        import torch

        self._load()
        tok, model = self._tok, self._model
        ids = tok(prompt, return_tensors="pt", add_special_tokens=False).input_ids.to(self._device)
        with torch.inference_mode():
            full = torch.softmax(model(input_ids=ids).logits[0, -1].float(), dim=-1)
        # The answer letter may be tokenised with or without a leading space.
        mass = []
        for lbl in labels:
            variants = {tok(v, add_special_tokens=False).input_ids[0] for v in (lbl, " " + lbl)}
            mass.append(sum(full[i].item() for i in variants))
        total = sum(mass) or 1.0
        return [m / total for m in mass]


class PhraseRanker:
    def __init__(self, scorer: LogitScorer, shortlist: int = 6):
        self.scorer = scorer
        self.shortlist = min(shortlist, len(LETTERS))

    def rank(self, typed: str, recent: list[str], hour: int, phrases: list[str], examples: list[str]) -> tuple[list[tuple[str, float]], float]:
        phrases = list(dict.fromkeys(p for p in phrases if p.strip()))
        ctx = context_text(typed, recent, hour, examples)
        prefix = ctx + "\nجمله‌ی بعدی بیمار:"
        # Step 1: one batched pass over every candidate.
        seq = self.scorer.sequence_scores(prefix, phrases)
        order = sorted(range(len(phrases)), key=lambda i: seq[i], reverse=True)
        base = softmax(seq, temperature=0.5)
        top = order[: self.shortlist]
        if len(top) < 2:
            only = [(phrases[i], base[i]) for i in order]
            return only, (only[0][1] if only else 0.0)
        # Step 2: a choice among the shortlist, read from the logits of the answer letter.
        labels = list(LETTERS[: len(top)])
        options = "\n".join(f"{lbl}) {phrases[i]}" for lbl, i in zip(labels, top))
        prompt = f"{ctx}\nبیمار احتمالاً الان کدام را می‌خواهد بگوید؟\n{options}\nپاسخ (فقط حرف گزینه):"
        choice = self.scorer.choice_distribution(prompt, labels)
        # Blend the two views; the choice head is better calibrated among close options.
        top_mass = sum(base[i] for i in top)
        blended = {i: top_mass * (0.7 * choice[j] + 0.3 * base[i] / top_mass) for j, i in enumerate(top)}
        ranked = sorted(((phrases[i], p) for i, p in blended.items()), key=lambda x: x[1], reverse=True)
        rest = [(phrases[i], base[i]) for i in order[len(top):]]
        confidence = max(choice)
        return ranked + rest, confidence
