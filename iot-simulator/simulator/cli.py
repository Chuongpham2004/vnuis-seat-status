"""Command line entry point.

    python -m simulator run [--interactive]      start all virtual seats + control API
    python -m simulator sit A-02                 one-shot commands against a running simulator
    python -m simulator shell                    interactive console against a running simulator
"""

from __future__ import annotations

import logging
import threading
from typing import Optional

import typer
import uvicorn

from simulator.commands import HELP, HttpBackend, ManagerBackend, execute
from simulator.config import Settings

app = typer.Typer(add_completion=False, help="Virtual seat nodes for the Smart Self-Study Space platform.")

API_OPTION = typer.Option("http://127.0.0.1:8090", "--api", envvar="SIM_API", help="Control API of a running simulator")


@app.command()
def run(
    mode: Optional[str] = typer.Option(None, help="ambient | manual"),
    speed: Optional[float] = typer.Option(None, help="Simulated seconds per real second (60 = 1 h per minute)"),
    seats: Optional[int] = typer.Option(None, help="Maximum number of virtual seats"),
    start_hour: Optional[float] = typer.Option(None, help="Start the simulated clock at this local hour"),
    host: Optional[str] = typer.Option(None, help="Control API bind address"),
    port: Optional[int] = typer.Option(None, help="Control API port"),
    interactive: bool = typer.Option(False, "--interactive", "-i", help="Also read commands from this terminal"),
    log_level: str = typer.Option("INFO"),
) -> None:
    """Connect every virtual seat to EMQX and serve the control API."""
    from simulator.control_api import create_app
    from simulator.manager import SimulatorManager
    from simulator.seats import load_seats

    logging.basicConfig(level=log_level.upper(), format="%(asctime)s %(levelname)-5s %(name)s - %(message)s")
    overrides = {k: v for k, v in {"mode": mode, "speed": speed, "sim_seats": seats, "start_hour": start_hour,
                                   "control_host": host, "control_port": port}.items() if v is not None}
    settings = Settings(**overrides)
    manager = SimulatorManager(settings, load_seats(settings))
    manager.start()

    if interactive:
        threading.Thread(target=_console, args=(ManagerBackend(manager),), daemon=True, name="console").start()

    try:
        uvicorn.run(create_app(manager), host=settings.control_host, port=settings.control_port, log_level="warning")
    finally:
        manager.stop()


def _console(backend) -> None:
    print(HELP)
    while True:
        try:
            line = input("sim> ")
        except EOFError:
            return
        try:
            out = execute(line, backend)
        except SystemExit:
            print("console closed (simulator keeps running; Ctrl+C to stop)")
            return
        if out:
            print(out)


@app.command()
def shell(api: str = API_OPTION) -> None:
    """Interactive console for a simulator that is already running (e.g. in Docker)."""
    _console(HttpBackend(api))


def _one_shot(verb: str, seat: str, api: str) -> None:
    typer.echo(execute(f"{verb} {seat}", HttpBackend(api)))


@app.command()
def sit(seat: str, api: str = API_OPTION) -> None:
    """Someone sits down on SEAT (publishes < 50 cm)."""
    _one_shot("sit", seat, api)


@app.command()
def stand(seat: str, api: str = API_OPTION) -> None:
    """The student leaves SEAT (publishes > 50 cm)."""
    _one_shot("stand", seat, api)


@app.command()
def disconnect(seat: str, api: str = API_OPTION) -> None:
    """Kill SEAT's device so the broker publishes its Last Will (OFFLINE)."""
    _one_shot("disconnect", seat, api)


@app.command()
def reconnect(seat: str, api: str = API_OPTION) -> None:
    """Bring SEAT's device back ONLINE."""
    _one_shot("reconnect", seat, api)


@app.command()
def release(seat: str, api: str = API_OPTION) -> None:
    """Hand SEAT back to ambient behaviour."""
    _one_shot("release", seat, api)


@app.command()
def status(seat: Optional[str] = typer.Argument(None), api: str = API_OPTION) -> None:
    """Simulator summary, or one seat."""
    typer.echo(execute(f"status {seat or ''}", HttpBackend(api)))


@app.command()
def mode(value: str = typer.Argument(..., help="ambient | manual"), api: str = API_OPTION) -> None:
    """Switch every unpinned seat between automatic and manual behaviour."""
    typer.echo(execute(f"mode {value}", HttpBackend(api)))


@app.command()
def scenario(name: str = typer.Argument(..., help="rush_hour | mass_leave | reset"), api: str = API_OPTION) -> None:
    """Bulk demo action."""
    typer.echo(execute(f"scenario {name}", HttpBackend(api)))


if __name__ == "__main__":
    app()
