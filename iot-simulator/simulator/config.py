"""Settings from environment variables (same names as infra/docker-compose.yml)."""

from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # MQTT broker (EMQX). Credentials must match infra/emqx/auth-bootstrap.csv.
    mqtt_host: str = "localhost"
    mqtt_port: int = 1883
    mqtt_user: str = "simulator"
    mqtt_password: str = "simulator_dev_pw"
    mqtt_keepalive: int = 15  # broker fires the Last Will ~1.5x this after a silent drop

    # Seat list: fetched from backend-core if reachable, otherwise the built-in A/B/C x 20 layout.
    backend_url: str | None = "http://localhost:8080"
    sim_seats: int = 59  # at most this many virtual seats
    exclude_seats: str = "A-01"  # comma separated; A-01 is the physical ESP32

    # Behaviour
    mode: str = "ambient"  # "ambient" (automatic arrivals/departures) or "manual"
    speed: float = 1.0  # simulated seconds per real second (60 = one hour per minute)
    start_hour: float | None = None  # start the simulated clock at this local hour (e.g. 9.5)
    tick_seconds: float = 1.0  # real seconds between scheduler ticks
    heartbeat_seconds: float = 30.0  # real seconds between heartbeats, like the firmware
    seed: int | None = None

    # HTTP control API used by the CLI and the defense demo
    control_host: str = "127.0.0.1"
    control_port: int = 8090

    def excluded(self) -> set[str]:
        return {s.strip().upper() for s in self.exclude_seats.split(",") if s.strip()}
