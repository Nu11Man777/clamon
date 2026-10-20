from __future__ import annotations

import asyncio
import codecs
import os
from collections.abc import Mapping
from dataclasses import dataclass

import httpx

from scraper import texto as conversion

PUERTO_POR_ESQUEMA = {"http": 80, "https": 443}
PUERTOS_PERMITIDOS = frozenset({80, 443})
MAX_REDIRECCIONES = 3
TIPOS_TEXTO = frozenset({"text/html", "text/plain"})


class ErrorEgreso(Exception):
    estado = 502
    codigo = "error"

    def __init__(self, detalle: str, motivo: str | None = None) -> None:
        super().__init__(detalle)
        self.detalle = detalle
        self.motivo = motivo


class UrlInvalida(ErrorEgreso):
    estado = 400
    codigo = "url_invalida"


class SitioNoPermitido(ErrorEgreso):
    estado = 403
    codigo = "sitio_no_permitido"


class ContenidoNoTexto(ErrorEgreso):
    estado = 415
    codigo = "contenido_no_texto"


class DescargaFallida(ErrorEgreso):
    estado = 502
    codigo = "descarga_fallida"


def normalizar_nombre(nombre: str) -> str:
    nombre = nombre.strip().lower()
    return nombre[:-1] if nombre.endswith(".") else nombre


def mismo_sitio(nombre: str) -> str:
    return nombre.removeprefix("www.")


@dataclass(frozen=True, slots=True)
class Configuracion:
    timeout_s: float = 10.0
    max_bytes: int = 2_000_000
    user_agent: str = "Clamon-Scraper/0.1"

    @classmethod
    def desde_entorno(cls, entorno: Mapping[str, str] = os.environ) -> Configuracion:
        return cls(
            timeout_s=float(entorno.get("TIMEOUT_S", "10")),
            max_bytes=int(entorno.get("MAX_BYTES", "2000000")),
            user_agent=entorno.get("USER_AGENT", "Clamon-Scraper/0.1"),
        )


@dataclass(frozen=True, slots=True)
class Destino:
    url: httpx.URL
    host: str
    puerto: int


def validar_url(url: str) -> Destino:
    try:
        u = httpx.URL(url)
    except (httpx.InvalidURL, TypeError, ValueError) as exc:
        raise UrlInvalida("la URL no se puede interpretar") from exc
    esquema = u.scheme.lower()
    if esquema not in PUERTO_POR_ESQUEMA:
        raise SitioNoPermitido(f"esquema {esquema or 'vacio'} no permitido", motivo="esquema")
    if not u.host:
        raise UrlInvalida("la URL no tiene nombre de servidor")
    if u.userinfo:
        raise UrlInvalida("la URL no puede incluir credenciales")
    puerto = u.port or PUERTO_POR_ESQUEMA[esquema]
    if puerto not in PUERTOS_PERMITIDOS:
        raise SitioNoPermitido(f"puerto {puerto} no permitido", motivo="puerto")
    return Destino(u, normalizar_nombre(u.host), puerto)


def _decodificar(contenido: bytes, codificacion: str | None) -> str:
    try:
        codecs.lookup(codificacion or "utf-8")
    except LookupError:
        codificacion = "utf-8"
    return contenido.decode(codificacion or "utf-8", errors="replace")


async def _descargar(cliente: httpx.AsyncClient, destino: Destino, config: Configuracion) -> dict:
    for salto in range(MAX_REDIRECCIONES + 1):
        async with cliente.stream("GET", destino.url) as respuesta:
            if respuesta.is_redirect:
                if salto == MAX_REDIRECCIONES:
                    raise SitioNoPermitido(
                        f"mas de {MAX_REDIRECCIONES} redirecciones", motivo="redirecciones"
                    )
                siguiente = destino.url.join(respuesta.headers["location"])
                siguiente_destino = validar_url(str(siguiente))
                if mismo_sitio(siguiente_destino.host) != mismo_sitio(destino.host):
                    raise SitioNoPermitido(
                        f"redireccion hacia otro sitio: {siguiente_destino.host}",
                        motivo="otro_sitio",
                    )
                destino = siguiente_destino
                continue
            if respuesta.status_code >= 400:
                raise DescargaFallida(f"el sitio respondio {respuesta.status_code}")
            tipo = respuesta.headers.get("content-type", "")
            mime = tipo.split(";", 1)[0].strip().lower()
            if mime not in TIPOS_TEXTO:
                raise ContenidoNoTexto(f"tipo de contenido {mime or 'desconocido'} no permitido")

            contenido = bytearray()
            truncado = False
            async for trozo in respuesta.aiter_bytes():
                disponible = config.max_bytes - len(contenido)
                if len(trozo) > disponible:
                    contenido.extend(trozo[:disponible])
                    truncado = True
                    break
                contenido.extend(trozo)

            decodificado = _decodificar(bytes(contenido), respuesta.encoding)
            if mime == "text/html":
                texto = conversion.html_a_texto(decodificado)
            else:
                texto = conversion.normalizar_texto_plano(decodificado)
            texto, recortado = conversion.recortar(texto)
            return {
                "url": str(destino.url),
                "host": destino.host,
                "estado_http": respuesta.status_code,
                "tipo_contenido": tipo,
                "bytes": len(contenido),
                "texto": texto,
                "truncado": truncado or recortado,
            }
    raise SitioNoPermitido("redirecciones agotadas", motivo="redirecciones")  # pragma: no cover


async def extraer(
    url: str, config: Configuracion, transporte: httpx.AsyncBaseTransport | None = None
) -> dict:
    destino = validar_url(url)
    async with httpx.AsyncClient(
        transport=transporte,
        timeout=httpx.Timeout(config.timeout_s),
        follow_redirects=False,
        trust_env=False,
        headers={"User-Agent": config.user_agent},
    ) as cliente:
        try:
            async with asyncio.timeout(config.timeout_s):
                return await _descargar(cliente, destino, config)
        except TimeoutError as exc:
            raise DescargaFallida(f"tiempo agotado {config.timeout_s:g} s") from exc
        except httpx.HTTPError as exc:
            raise DescargaFallida(f"error de red: {type(exc).__name__}") from exc
