"""Cheshm-Goya AI server: an optional, local-only layer that makes the tablet app smarter.

Everything runs on this computer. No request is ever forwarded to an external service.
"""
from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from dataclasses import dataclass

from fastapi import Depends, FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import HTMLResponse, JSONResponse, Response
from starlette.concurrency import run_in_threadpool

from . import schemas
from .config import settings
from .discovery import Advertiser
from .logging_utils import configure_logging, log
from .pairing import pairing_page
from .security import get_server_id, get_token, is_lan_address, require_loopback, require_token
from .services import rules
from .services.llm import LanguageService, OllamaBackend
from .services.personalization import PersonalStore
from .services.ranker import HFScorer, PhraseRanker
from .services.stt import WhisperSTT
from .services.tts import KNOWN_VOICES, PiperTTS
from .services.voice import VoiceBank

VERSION = "1.0.0"
MAX_AUDIO_BYTES = 25 * 1024 * 1024


@dataclass
class Services:
    language: LanguageService
    ranker: PhraseRanker
    stt: WhisperSTT
    tts: PiperTTS
    voices: VoiceBank
    personal: PersonalStore


def default_services() -> Services:
    return Services(
        language=LanguageService(OllamaBackend(settings.ollama_url, settings.llm_model), settings.llm_model),
        ranker=PhraseRanker(HFScorer(settings.ranker_model, settings.ranker_device)),
        stt=WhisperSTT(settings.whisper_model, settings.whisper_compute_type),
        tts=PiperTTS(settings.voices_dir, settings.default_voice),
        voices=VoiceBank(settings.data_dir / "voices"),
        personal=PersonalStore(settings.data_dir / "personal.db"),
    )


def create_app(services: Services | None = None, advertise: bool | None = None) -> FastAPI:
    configure_logging()
    svc = services or default_services()
    do_advertise = settings.mdns if advertise is None else advertise

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        get_token()  # create on first start
        adv = Advertiser(settings.server_name, settings.port, get_server_id()) if do_advertise else None
        if adv:
            await adv.start()
        if not settings.lazy_models:
            # Warm up heavy models in the background so the first request is fast.
            asyncio.get_running_loop().run_in_executor(None, _warm_up, svc)
        log.info("Cheshm-Goya server ready. Pair the tablet at http://localhost:%d/pair", settings.port)
        yield
        if adv:
            await adv.stop()

    app = FastAPI(title="Cheshm-Goya AI server", version=VERSION, lifespan=lifespan, docs_url=None, redoc_url=None)
    app.state.services = svc
    auth = [Depends(require_token)]

    @app.middleware("http")
    async def lan_only(request: Request, call_next):
        host = request.client.host if request.client else None
        if settings.lan_only and not is_lan_address(host):
            return JSONResponse({"detail": "only local-network clients are allowed"}, status_code=403)
        return await call_next(request)

    @app.get("/health", response_model=schemas.Health, dependencies=auth)
    async def health():
        return schemas.Health(
            status="ok",
            version=VERSION,
            models={
                "llm": await svc.language.backend.available(),
                "ranker_loaded": getattr(svc.ranker.scorer, "loaded", True),
                "stt_loaded": svc.stt.loaded,
                "tts": svc.tts.loaded,
                "voice_cloning": svc.voices.cloning_available,
            },
        )

    def examples_for(typed: str) -> list[str]:
        return svc.personal.similar(typed, 5) if svc.personal.count() else []

    @app.post("/complete", response_model=schemas.CompleteResponse, dependencies=auth)
    async def complete(req: schemas.CompleteRequest):
        try:
            out = await svc.language.complete(req.typed, req.recent, req.hour, examples_for(req.typed), req.n)
        except Exception as e:
            log.warning("complete failed: %s", type(e).__name__)
            raise HTTPException(503, "language model unavailable")
        return schemas.CompleteResponse(suggestions=out, model=svc.language.model_name)

    @app.post("/rank", response_model=schemas.RankResponse, dependencies=auth)
    async def rank(req: schemas.RankRequest):
        c = req.context
        try:
            ranked, conf = await run_in_threadpool(svc.ranker.rank, c.typed, c.recent, c.hour, req.phrases, examples_for(c.typed))
        except Exception as e:
            log.warning("rank failed: %s", type(e).__name__)
            raise HTTPException(503, "ranker unavailable")
        return schemas.RankResponse(
            ranked=[schemas.RankedPhrase(text=t, probability=round(p, 5)) for t, p in ranked],
            confidence=round(conf, 4),
            model=getattr(svc.ranker.scorer, "name", ""),
        )

    @app.post("/classify", response_model=schemas.ClassifyResponse, dependencies=auth)
    async def classify(req: schemas.ClassifyRequest):
        try:
            kind, topic, conf = await svc.language.classify(req.text)
        except Exception:
            kind, topic, conf = rules.classify(req.text)
        return schemas.ClassifyResponse(type=kind, topic=topic, confidence=round(conf, 3))

    @app.post("/stt", response_model=schemas.SttResponse, dependencies=auth)
    async def stt(audio: UploadFile = File(...), language: str = Form("fa")):
        data = await audio.read()
        if not data or len(data) > MAX_AUDIO_BYTES:
            raise HTTPException(413 if data else 400, "audio missing or too large")
        try:
            text = await run_in_threadpool(svc.stt.transcribe, data, language)
        except Exception as e:
            log.warning("stt failed: %s", type(e).__name__)
            raise HTTPException(503, "speech recognition unavailable")
        return schemas.SttResponse(text=text, language=language)

    @app.post("/tts", dependencies=auth, response_class=Response)
    async def tts(req: schemas.TtsRequest):
        try:
            if req.voice.startswith("personal-"):
                wav = await run_in_threadpool(svc.voices.synthesize, req.voice, req.text)
                if wav is not None:
                    return Response(wav, media_type="audio/wav")
                # Personal voice not available yet: fall back to the natural default voice.
                wav = await run_in_threadpool(svc.tts.synthesize, req.text, "default")
                return Response(wav, media_type="audio/wav", headers={"X-Voice-Fallback": "default"})
            wav = await run_in_threadpool(svc.tts.synthesize, req.text, req.voice)
        except Exception as e:
            log.warning("tts failed: %s", type(e).__name__)
            raise HTTPException(503, "speech synthesis unavailable")
        return Response(wav, media_type="audio/wav")

    @app.get("/voices", response_model=schemas.VoicesResponse, dependencies=auth)
    async def voices():
        out = [schemas.Voice(id=v, name=KNOWN_VOICES.get(v, v)) for v in svc.tts.available_voices()]
        out += [schemas.Voice(id=i, name=n, personal=True) for i, n in svc.voices.personal_voices()]
        return schemas.VoicesResponse(voices=out)

    @app.post("/voice/enroll", response_model=schemas.EnrollResponse, dependencies=auth)
    async def voice_enroll(name: str = Form("patient"), files: list[UploadFile] = File(...)):
        blobs = []
        for f in files:
            data = await f.read()
            if len(data) > MAX_AUDIO_BYTES:
                raise HTTPException(413, f"{f.filename} is too large")
            blobs.append((f.filename or "sample.wav", data))
        result = await run_in_threadpool(svc.voices.enroll, name, blobs)
        return schemas.EnrollResponse(**result)

    @app.post("/personalize/sentences", response_model=schemas.SyncResponse, dependencies=auth)
    async def personalize(req: schemas.SyncRequest):
        stored = sum(1 for s in req.sentences if svc.personal.add(s.text, s.ts))
        return schemas.SyncResponse(stored=stored)

    @app.get("/pair", response_class=HTMLResponse, dependencies=[Depends(require_loopback)])
    async def pair():
        return HTMLResponse(pairing_page())

    return app


def _warm_up(svc: Services) -> None:
    try:
        svc.ranker.rank("", [], 12, ["آب می‌خوام", "درد دارم"], [])
    except Exception as e:
        log.warning("ranker warm-up failed: %s", e)
    try:
        svc.stt._load()
    except Exception as e:
        log.warning("whisper warm-up failed: %s", e)


def run() -> None:
    import uvicorn

    uvicorn.run(create_app(), host=settings.host, port=settings.port, log_level="info", access_log=False)


if __name__ == "__main__":
    run()
