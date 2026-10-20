from __future__ import annotations

import contextvars
import json
import logging
import os
import re
import sys
import urllib.request
from datetime import UTC, datetime
from typing import Any

_TRACEPARENT = re.compile(r"^[0-9a-f]{2}-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}$")
_CLOUD_TRACE = re.compile(r"^([0-9a-f]{32})(?:/(\d+))?")
_METADATOS_PROYECTO = "http://metadata.google.internal/computeMetadata/v1/project/project-id"

_traza: contextvars.ContextVar[tuple[str, str | None] | None] = contextvars.ContextVar(
    "traza", default=None
)
_servicio = "desconocido"
_proyecto: str | None = None

log = logging.getLogger("clamon")


def extraer_traza(
    traceparent: str | None, cloud_trace: str | None
) -> tuple[str, str | None] | None:
    if traceparent:
        m = _TRACEPARENT.match(traceparent.strip().lower())
        if m and m.group(1) != "0" * 32:
            return m.group(1), m.group(2)
    if cloud_trace:
        m = _CLOUD_TRACE.match(cloud_trace.strip().lower())
        if m:
            span = format(int(m.group(2)) & 0xFFFFFFFFFFFFFFFF, "016x") if m.group(2) else None
            return m.group(1), span
    return None


def _proyecto_gcp() -> str | None:
    proyecto = os.environ.get("GOOGLE_CLOUD_PROJECT")
    if proyecto:
        return proyecto
    if not (os.environ.get("K_SERVICE") or os.environ.get("CLOUD_RUN_JOB")):
        return None
    try:
        peticion = urllib.request.Request(
            _METADATOS_PROYECTO, headers={"Metadata-Flavor": "Google"}
        )
        with urllib.request.urlopen(peticion, timeout=2) as respuesta:  # noqa: S310 URL fija
            return respuesta.read().decode().strip() or None
    except OSError:
        return None


class FormatoJson(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        entrada: dict[str, Any] = {
            "severity": record.levelname,
            "message": record.getMessage(),
            "servicio": _servicio,
            "time": datetime.fromtimestamp(record.created, UTC).isoformat(),
        }
        campos = getattr(record, "campos", None)
        if campos:
            entrada.update(campos)
        traza = _traza.get()
        if traza and _proyecto:
            entrada["logging.googleapis.com/trace"] = f"projects/{_proyecto}/traces/{traza[0]}"
            if traza[1]:
                entrada["logging.googleapis.com/spanId"] = traza[1]
        if record.exc_info:
            entrada["excepcion"] = self.formatException(record.exc_info)
        return json.dumps(entrada, ensure_ascii=False, default=str)


class _SalidaEstandar(logging.StreamHandler):
    @property
    def stream(self):  # type: ignore[override]
        return sys.stdout

    @stream.setter
    def stream(self, _valor) -> None:
        pass


def configurar(servicio: str) -> None:
    global _servicio, _proyecto
    _servicio = servicio
    _proyecto = _proyecto_gcp()
    manejador = _SalidaEstandar()
    manejador.setFormatter(FormatoJson())
    raiz = logging.getLogger()
    raiz.handlers[:] = [manejador]
    raiz.setLevel(os.environ.get("LOG_LEVEL", "INFO").upper())


def registrar(severidad: int, mensaje: str, evento: str | None = None, **campos: Any) -> None:
    if evento:
        campos = {"evento": evento, **campos}
    log.log(severidad, mensaje, extra={"campos": campos})


class MiddlewareTraza:
    def __init__(self, app: Any) -> None:
        self.app = app

    async def __call__(self, scope: dict, receive: Any, send: Any) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        encabezados = {
            k.decode("latin-1").lower(): v.decode("latin-1") for k, v in scope["headers"]
        }
        token = _traza.set(
            extraer_traza(encabezados.get("traceparent"), encabezados.get("x-cloud-trace-context"))
        )
        try:
            await self.app(scope, receive, send)
        finally:
            _traza.reset(token)
