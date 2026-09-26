import io
import wave

import pytest
from fastapi.testclient import TestClient

from app.main import Services, create_app
from app.security import get_token, is_lan_address
from app.services.llm import LanguageService
from app.services.personalization import PersonalStore
from app.services.ranker import PhraseRanker, softmax
from app.services.tts import PiperTTS
from app.services.voice import VoiceBank


class FakeChat:
    def __init__(self, reply=None, fail=False):
        self.reply = reply or {"sentences": ["۱. سرم درد می‌کنه", "«سردمه»", "سرم درد می‌کنه", "آب می‌خوام"]}
        self.fail = fail
        self.prompts = []

    async def chat_json(self, system, user, schema, max_tokens):
        self.prompts.append(user)
        if self.fail:
            raise RuntimeError("down")
        if "type" in schema.get("properties", {}):
            return {"type": "yes_no", "topic": "needs", "confidence": 0.8}
        return self.reply

    async def available(self):
        return not self.fail


class FakeScorer:
    """Pretends the model prefers phrases containing 'آب'."""
    name = "fake-scorer"
    loaded = True

    def __init__(self):
        self.calls = 0

    def sequence_scores(self, prefix, continuations):
        self.calls += 1
        return [(-0.5 if "آب" in c else -2.0) - 0.01 * i for i, c in enumerate(continuations)]

    def choice_distribution(self, prompt, labels):
        self.calls += 1
        return softmax([3.0] + [0.0] * (len(labels) - 1))


class FakeSTT:
    loaded = True

    def transcribe(self, audio, language="fa"):
        return "آب می‌خوای؟"


class FakeTTS(PiperTTS):
    def available_voices(self):
        return ["fa_IR-amir-medium"]

    def synthesize(self, text, voice="default"):
        buf = io.BytesIO()
        with wave.open(buf, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000); w.writeframes(b"\0\0" * 1600)
        return buf.getvalue()


@pytest.fixture
def chat():
    return FakeChat()


@pytest.fixture
def client(tmp_path, chat):
    from app.config import settings

    svc = Services(
        language=LanguageService(chat, "fake-llm"),
        ranker=PhraseRanker(FakeScorer()),
        stt=FakeSTT(),
        tts=FakeTTS(tmp_path, "fa_IR-amir-medium"),
        voices=VoiceBank(settings.data_dir / "voices"),
        personal=PersonalStore(settings.data_dir / "personal.db"),
    )
    with TestClient(create_app(svc, advertise=False)) as c:
        c.headers["Authorization"] = f"Bearer {get_token()}"
        yield c


def test_requires_token(client):
    r = client.post("/complete", json={"typed": "سر"}, headers={"Authorization": "Bearer wrong"})
    assert r.status_code == 401
    r = client.get("/health", headers={"Authorization": ""})
    assert r.status_code == 401


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"
    assert r.json()["models"]["tts"] is True


def test_complete_cleans_and_dedupes(client):
    r = client.post("/complete", json={"typed": "سر", "recent": ["آب می‌خوام"], "hour": 22})
    assert r.status_code == 200
    assert r.json()["suggestions"] == ["سرم درد می‌کنه", "سردمه", "آب می‌خوام"]


def test_complete_prompt_uses_context_and_personal_examples(client, chat):
    client.post("/personalize/sentences", json={"sentences": [{"text": "سرمو بخارونید", "ts": 1}]})
    client.post("/complete", json={"typed": "سرم", "recent": ["سلام"], "hour": 7})
    p = chat.prompts[-1]
    assert "سرمو بخارونید" in p and "سلام" in p and "صبح" in p and "«سرم»" in p


def test_complete_503_when_llm_down(tmp_path):
    from app.config import settings

    svc = Services(LanguageService(FakeChat(fail=True), "x"), PhraseRanker(FakeScorer()), FakeSTT(),
                   FakeTTS(tmp_path, "v"), VoiceBank(tmp_path / "v"), PersonalStore(settings.data_dir / "p.db"))
    with TestClient(create_app(svc, advertise=False)) as c:
        r = c.post("/complete", json={"typed": ""}, headers={"Authorization": f"Bearer {get_token()}"})
        assert r.status_code == 503  # the app then silently uses offline prediction
        # classify still works through the rules
        r = c.post("/classify", json={"text": "درد داری؟"}, headers={"Authorization": f"Bearer {get_token()}"})
        assert r.json() == {"type": "yes_no", "topic": "pain", "confidence": 0.7}


def test_rank_returns_probabilities_and_confidence(client):
    phrases = ["درد دارم", "آب می‌خوام", "می‌ترسم", "تشنمه", "آب سرد", "پتو", "چراغ", "پنجره"]
    r = client.post("/rank", json={"context": {"typed": "", "recent": [], "hour": 13}, "phrases": phrases})
    assert r.status_code == 200
    body = r.json()
    ranked = [x["text"] for x in body["ranked"]]
    assert ranked[0] == "آب می‌خوام"
    assert sorted(ranked) == sorted(phrases)
    assert 0 < body["confidence"] <= 1
    assert sum(x["probability"] for x in body["ranked"]) == pytest.approx(1.0, abs=1e-3)


def test_classify(client):
    r = client.post("/classify", json={"text": "آب می‌خوای؟"})
    assert r.json()["type"] == "yes_no"
    assert r.json()["topic"] == "needs"


def test_stt_and_tts(client):
    r = client.post("/stt", files={"audio": ("q.wav", b"RIFF....", "audio/wav")}, data={"language": "fa"})
    assert r.json()["text"] == "آب می‌خوای؟"
    r = client.post("/tts", json={"text": "سلام", "voice": "default"})
    assert r.status_code == 200 and r.headers["content-type"] == "audio/wav" and r.content[:4] == b"RIFF"


def test_personal_voice_falls_back_honestly(client):
    wav = FakeTTS(None, "").synthesize("x")
    r = client.post("/voice/enroll", data={"name": "baba"}, files=[("files", ("a.wav", wav, "audio/wav")), ("files", ("x.exe", b"MZ", "application/octet-stream"))])
    body = r.json()
    assert body["files_stored"] == 1 and body["cloning_available"] is False
    r = client.post("/tts", json={"text": "سلام", "voice": body["voice_id"]})
    assert r.status_code == 200 and r.headers["x-voice-fallback"] == "default"
    voices = client.get("/voices").json()["voices"]
    assert voices == [{"id": "fa_IR-amir-medium", "name": "امیر (مرد)", "personal": False}]


def test_personalization_sync_and_retrieval(client):
    r = client.post("/personalize/sentences", json={"sentences": [
        {"text": "پتو رو بکشید رو پام", "ts": 1}, {"text": "پتو رو بکشید رو پام", "ts": 2}, {"text": "دخترم رو صدا کنید", "ts": 3}]})
    assert r.json()["stored"] == 3
    store = client.app.state.services.personal
    assert store.count() == 2
    assert store.similar("پتو")[0] == "پتو رو بکشید رو پام"


def test_pairing_page_only_on_server_computer(client):
    r = client.get("/pair")  # TestClient counts as loopback
    assert r.status_code == 200 and "QR" in r.text and get_token() in r.text


def test_lan_only_filter():
    assert is_lan_address("192.168.1.20")
    assert is_lan_address("100.101.102.103")  # Tailscale
    assert is_lan_address("::ffff:10.0.0.5")
    assert not is_lan_address("8.8.8.8")
    assert not is_lan_address("2001:4860:4860::8888")


def test_logs_do_not_contain_message_text(client, caplog):
    import logging

    caplog.set_level(logging.INFO)
    client.post("/complete", json={"typed": "رازِ خصوصی"})
    assert "رازِ خصوصی" not in caplog.text
    assert "chars>" in caplog.text


def test_token_is_stable_and_private(tmp_path):
    from app.config import settings

    t1 = get_token()
    assert get_token() == t1 and len(t1) >= 32
    assert (settings.token_file.stat().st_mode & 0o077) == 0
