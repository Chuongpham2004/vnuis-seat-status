FROM python:3.11-slim

# OpenCV (headless) still needs libGL-free runtime libs only.
RUN apt-get update && apt-get install -y --no-install-recommends libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 MODEL_DIR=/app/models

COPY pyproject.toml ./
COPY app ./app
RUN pip install .

# Models are baked into the image (verified by SHA-256), so the container starts offline.
COPY scripts ./scripts
RUN python scripts/download_models.py /app/models \
    && useradd --create-home face && chown -R face /app
USER face

EXPOSE 8001
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=10 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8001/health').status==200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
