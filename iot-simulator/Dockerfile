FROM python:3.11-slim

WORKDIR /app
ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    CONTROL_HOST=0.0.0.0

COPY pyproject.toml ./
COPY simulator ./simulator
RUN pip install . && useradd --create-home sim
USER sim

EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8090/health').status==200 else 1)"

# MQTT_HOST, MQTT_USER, MQTT_PASSWORD, BACKEND_URL, SIM_SEATS, MODE, SPEED come from the environment.
CMD ["python", "-m", "simulator", "run"]
