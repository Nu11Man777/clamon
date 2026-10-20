from __future__ import annotations

import logging
import os
import time

import httpx
import uvicorn
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException as StarletteHTTPException

from scraper import egreso, registro

SERVICIO = "clamon-scraper"

registro.configurar(SERVICIO)

app = FastAPI(title=SERVICIO, docs_url=None, redoc_url=None, openapi_url=None)
app.add_middleware(registro.MiddlewareTraza)
app.state.config = egreso.Configuracion.desde_entorno()
app.state.transporte = None

registro.registrar(
    logging.INFO,
    "configuracion cargada",
    evento="scraper.configuracion",
    timeout_s=app.state.config.timeout_s,
    max_bytes=app.state.config.max_bytes,
)


class PeticionExtraer(BaseModel):
    url: str = Field(min_length=1, max_length=2048)


def _error(estado: int, codigo: str, detalle: str) -> JSONResponse:
    return JSONResponse(status_code=estado, content={"error": codigo, "detalle": detalle})


@app.exception_handler(RequestValidationError)
async def _validacion(_request: Request, _exc: RequestValidationError) -> JSONResponse:
    return _error(400, "url_invalida", 'se espera {"url": "http(s)://..."}')


@app.exception_handler(StarletteHTTPException)
async def _http(_request: Request, exc: StarletteHTTPException) -> JSONResponse:
    codigos = {404: "no_encontrado", 405: "metodo_no_permitido"}
    return _error(exc.status_code, codigos.get(exc.status_code, "error_http"), str(exc.detail))


@app.get("/salud")
async def salud() -> dict:
    return {"estado": "ok", "servicio": SERVICIO}


_EVENTOS = {
    egreso.UrlInvalida: "scraper.url_invalida",
    egreso.SitioNoPermitido: "scraper.sitio_no_permitido",
    egreso.ContenidoNoTexto: "scraper.contenido_no_texto",
    egreso.DescargaFallida: "scraper.descarga_fallida",
}


def _host(url: str) -> str | None:
    try:
        return httpx.URL(url).host or None
    except (httpx.InvalidURL, TypeError, ValueError):
        return None


@app.post("/extraer")
async def extraer(peticion: PeticionExtraer) -> JSONResponse:
    inicio = time.monotonic()
    try:
        resultado = await egreso.extraer(
            peticion.url, app.state.config, transporte=app.state.transporte
        )
    except egreso.ErrorEgreso as exc:
        registro.registrar(
            logging.WARNING,
            f"extraccion rechazada: {exc.codigo}",
            evento=_EVENTOS.get(type(exc), "scraper.error"),
            host=_host(peticion.url),
            motivo=exc.motivo,
            detalle=exc.detalle,
            duracion_ms=round((time.monotonic() - inicio) * 1000),
        )
        return _error(exc.estado, exc.codigo, exc.detalle)

    registro.registrar(
        logging.INFO,
        "extraccion completada",
        evento="scraper.extraccion",
        host=resultado["host"],
        estado_http=resultado["estado_http"],
        bytes=resultado["bytes"],
        truncado=resultado["truncado"],
        duracion_ms=round((time.monotonic() - inicio) * 1000),
    )
    return JSONResponse(resultado)


def ejecutar() -> None:
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=int(os.environ.get("PORT", "8080")),
        log_config=None,
        access_log=False,
        server_header=False,
    )
