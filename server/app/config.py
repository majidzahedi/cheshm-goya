"""Server configuration from environment variables / .env (see .env.example)."""
from __future__ import annotations

from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_prefix="CG_", extra="ignore")

    host: str = "0.0.0.0"
    port: int = 8765
    data_dir: Path = Path("data")
    server_name: str = "چشم‌گویا"
    # Address put in the pairing QR (e.g. the Tailscale IP). Empty = detected LAN IP.
    public_host: str = ""

    # Language model served by a local Ollama (never a cloud API).
    ollama_url: str = "http://127.0.0.1:11434"
    llm_model: str = "qwen3.5:9b"

    # Small Hugging Face model whose logits rank phrases (openjev-style).
    ranker_model: str = "Qwen/Qwen3-1.7B"
    ranker_device: str = "auto"  # auto | cuda | cpu

    # Speech to text.
    whisper_model: str = "large-v3-turbo"
    whisper_compute_type: str = "float16"  # int8_float16 for smaller GPUs, int8 on CPU

    # Text to speech (Piper voices, .onnx + .onnx.json in voices_dir).
    voices_dir: Path = Path("models/piper")
    default_voice: str = "fa_IR-amir-medium"

    # Voice cloning backend; "none" = only store recordings (voice banking).
    voice_clone_backend: str = "none"

    # Privacy: never write message text to logs unless explicitly enabled.
    log_message_text: bool = False
    # Only accept clients from private/LAN/Tailscale addresses.
    lan_only: bool = True
    # Advertise on the LAN with mDNS so the tablet finds us.
    mdns: bool = True

    # Skip loading heavy models (tests / CPU-only smoke runs).
    lazy_models: bool = True

    @property
    def token_file(self) -> Path:
        return self.data_dir / "token"

    @property
    def server_id_file(self) -> Path:
        return self.data_dir / "server_id"


settings = Settings()
