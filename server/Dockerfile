# Cheshm-Goya AI server (GPU). Build: docker compose build
FROM nvidia/cuda:12.6.3-cudnn-runtime-ubuntu24.04

ENV DEBIAN_FRONTEND=noninteractive \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_BREAK_SYSTEM_PACKAGES=1

RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 python3-pip python3-venv ffmpeg libsndfile1 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
RUN pip3 install --index-url https://download.pytorch.org/whl/cu126 torch
COPY requirements.txt .
RUN pip3 install -r requirements.txt

COPY app ./app
COPY scripts ./scripts

# Runs as an unprivileged user; data and models are mounted volumes.
RUN useradd -m -u 1000 cg || true
USER 1000
EXPOSE 8765
CMD ["python3", "-m", "app.main"]
