"""Wire format shared with the Android app (app/core/.../LocalServerProvider.kt)."""
from __future__ import annotations

from pydantic import BaseModel, Field


class AiContext(BaseModel):
    typed: str = ""
    recent: list[str] = Field(default_factory=list, max_length=20)
    hour: int = Field(12, ge=0, le=23)


class CompleteRequest(BaseModel):
    typed: str = Field("", max_length=500)
    recent: list[str] = Field(default_factory=list, max_length=20)
    hour: int = Field(12, ge=0, le=23)
    n: int = Field(3, ge=1, le=5)


class CompleteResponse(BaseModel):
    suggestions: list[str]
    model: str = ""


class RankRequest(BaseModel):
    context: AiContext
    phrases: list[str] = Field(..., min_length=1, max_length=300)


class RankedPhrase(BaseModel):
    text: str
    probability: float


class RankResponse(BaseModel):
    ranked: list[RankedPhrase]
    confidence: float
    model: str = ""


class ClassifyRequest(BaseModel):
    text: str = Field(..., max_length=500)


class ClassifyResponse(BaseModel):
    type: str  # yes_no | choice | open
    topic: str | None = None  # needs | pain | feelings | people
    confidence: float = 0.0


class SttResponse(BaseModel):
    text: str
    language: str = "fa"


class TtsRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=1000)
    voice: str = "default"


class Voice(BaseModel):
    id: str
    name: str
    personal: bool = False


class VoicesResponse(BaseModel):
    voices: list[Voice]


class EnrollResponse(BaseModel):
    voice_id: str
    files_stored: int
    total_seconds: float
    cloning_available: bool
    message: str


class SentenceItem(BaseModel):
    text: str = Field(..., max_length=500)
    ts: int


class SyncRequest(BaseModel):
    sentences: list[SentenceItem] = Field(..., max_length=1000)


class SyncResponse(BaseModel):
    stored: int


class Health(BaseModel):
    status: str
    version: str
    models: dict[str, bool]
