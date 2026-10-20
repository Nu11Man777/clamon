from __future__ import annotations

import logging
import os

import uvicorn
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException as StarletteHTTPException

from clasificador import detectores, registro

SERVICIO = "clamon-clasificador"
MAX_CARACTERES = 20_000

registro.configurar(SERVICIO)

app = FastAPI(title=SERVICIO, docs_url=None, redoc_url=None, openapi_url=None)
app.add_middleware(registro.MiddlewareTraza)


class PeticionClasificar(BaseModel):
    texto: str = Field(min_length=1, max_length=MAX_CARACTERES)


def _error(estado: int, codigo: str, detalle: str) -> JSONResponse:
    return JSONResponse(status_code=estado, content={"error": codigo, "detalle": detalle})


@app.exception_handler(RequestValidationError)
async def _validacion(_request: Request, exc: RequestValidationError) -> JSONResponse:
    detalle = "; ".join(
        f"{'.'.join(str(p) for p in e.get('loc', ()) if p != 'body')}: {e.get('msg', '')}"
        for e in exc.errors()
    )
    return _error(422, "validacion", detalle)


@app.exception_handler(StarletteHTTPException)
async def _http(_request: Request, exc: StarletteHTTPException) -> JSONResponse:
    codigos = {404: "no_encontrado", 405: "metodo_no_permitido"}
    return _error(exc.status_code, codigos.get(exc.status_code, "error_http"), str(exc.detail))


@app.get("/salud")
async def salud() -> dict:
    return {"estado": "ok", "servicio": SERVICIO}


@app.post("/clasificar")
async def clasificar(peticion: PeticionClasificar) -> dict:
    resultado = detectores.clasificar(peticion.texto)
    registro.registrar(
        logging.INFO,
        "clasificacion completada",
        evento="clasificador.clasificacion",
        nivel=resultado["nivel"],
        total=resultado["total"],
        categorias=resultado["categorias"],
        caracteres=len(peticion.texto),
    )
    return resultado


def ejecutar() -> None:
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=int(os.environ.get("PORT", "8080")),
        log_config=None,
        access_log=False,
        server_header=False,
    )
