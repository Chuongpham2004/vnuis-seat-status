FROM python:3.11-slim

# LightGBM needs the OpenMP runtime.
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 MODEL_DIR=/app/models

COPY pyproject.toml ./
COPY app ./app
RUN pip install . && useradd --create-home ai && mkdir -p /app/models && chown ai /app/models
USER ai

EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=10 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8000/health').status==200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
