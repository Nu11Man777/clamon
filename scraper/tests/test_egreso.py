import httpx
import pytest

from scraper import egreso
from scraper.egreso import (
    Configuracion,
    ContenidoNoTexto,
    DescargaFallida,
    SitioNoPermitido,
    UrlInvalida,
    extraer,
    validar_url,
)

pytestmark = pytest.mark.anyio

HTML = (
    b"<html><head><title>Example Domain</title><style>p{color:red}</style></head>"
    b"<body><h1>Example Domain</h1><script>alert(1)</script>"
    b"<p>This domain is for use in documentation examples.</p></body></html>"
)
CONFIG = Configuracion(
    timeout_s=5,
    max_bytes=2_000_000,
    user_agent="Clamon-Scraper/0.1",
)


def transporte(manejador):
    return httpx.MockTransport(manejador)


def responder_html(request: httpx.Request) -> httpx.Response:
    return httpx.Response(200, headers={"content-type": "text/html; charset=UTF-8"}, content=HTML)


async def test_nombre_permitido_descarga_y_convierte_html():
    vistos: list[httpx.Request] = []

    def manejador(request: httpx.Request) -> httpx.Response:
        vistos.append(request)
        return responder_html(request)

    r = await extraer("https://example.com/", CONFIG, transporte(manejador))
    assert r["host"] == "example.com"
    assert r["estado_http"] == 200
    assert r["tipo_contenido"] == "text/html; charset=UTF-8"
    assert r["bytes"] == len(HTML)
    assert r["truncado"] is False
    assert "This domain is for use in documentation examples." in r["texto"]
    assert "alert" not in r["texto"] and "color" not in r["texto"]
    assert vistos[0].headers["user-agent"] == "Clamon-Scraper/0.1"


async def test_cualquier_sitio_que_envia_el_backend():
    r = await extraer("https://www.ipn.mx/", CONFIG, transporte(responder_html))
    assert r["host"] == "www.ipn.mx"


async def test_esquema_file():
    with pytest.raises(SitioNoPermitido) as exc:
        await extraer("file:///etc/passwd", CONFIG, transporte(responder_html))
    assert exc.value.motivo == "esquema"


async def test_puerto_8080():
    with pytest.raises(SitioNoPermitido) as exc:
        await extraer("http://example.com:8080/", CONFIG, transporte(responder_html))
    assert exc.value.motivo == "puerto"


async def test_redireccion_hacia_otro_sitio():
    def manejador(request: httpx.Request) -> httpx.Response:
        if request.url.host == "example.com":
            return httpx.Response(302, headers={"location": "https://evil.example.net/"})
        raise AssertionError("no debe seguir la redireccion")

    with pytest.raises(SitioNoPermitido) as exc:
        await extraer("https://example.com/", CONFIG, transporte(manejador))
    assert exc.value.motivo == "otro_sitio"


async def test_redireccion_al_mismo_sitio_con_www():
    def manejador(request: httpx.Request) -> httpx.Response:
        if request.url.host == "example.com":
            return httpx.Response(301, headers={"location": "https://www.example.com/inicio"})
        return httpx.Response(200, headers={"content-type": "text/plain"}, content=b"hola  mundo")

    r = await extraer("https://example.com/", CONFIG, transporte(manejador))
    assert r["url"] == "https://www.example.com/inicio"
    assert r["host"] == "www.example.com"
    assert r["texto"] == "hola mundo"


async def test_redireccion_hacia_puerto_no_permitido():
    def manejador(request: httpx.Request) -> httpx.Response:
        return httpx.Response(302, headers={"location": "http://example.com:22/"})

    with pytest.raises(SitioNoPermitido) as exc:
        await extraer("https://example.com/", CONFIG, transporte(manejador))
    assert exc.value.motivo == "puerto"


async def test_mas_de_tres_redirecciones():
    saltos: list[str] = []

    def manejador(request: httpx.Request) -> httpx.Response:
        saltos.append(str(request.url))
        return httpx.Response(302, headers={"location": f"/salto{len(saltos)}"})

    with pytest.raises(SitioNoPermitido) as exc:
        await extraer("https://example.com/", CONFIG, transporte(manejador))
    assert exc.value.motivo == "redirecciones"
    assert len(saltos) == 4


async def test_contenido_no_texto():
    def manejador(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, headers={"content-type": "application/pdf"}, content=b"%PDF")

    with pytest.raises(ContenidoNoTexto):
        await extraer("https://example.com/", CONFIG, transporte(manejador))


async def test_limite_de_bytes():
    config = Configuracion(timeout_s=5, max_bytes=100)

    def manejador(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, headers={"content-type": "text/plain"}, content=b"a" * 1000)

    r = await extraer("https://example.com/", config, transporte(manejador))
    assert r["bytes"] == 100
    assert r["truncado"] is True


async def test_texto_se_recorta_a_20000_caracteres():
    def manejador(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, headers={"content-type": "text/plain"}, content=b"ab " * 10_000)

    r = await extraer("https://example.com/", CONFIG, transporte(manejador))
    assert len(r["texto"]) == 20_000
    assert r["truncado"] is True


async def test_error_http_del_sitio():
    def manejador(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, headers={"content-type": "text/html"}, content=b"error")

    with pytest.raises(DescargaFallida):
        await extraer("https://example.com/", CONFIG, transporte(manejador))


async def test_error_de_red():
    def manejador(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectTimeout("tiempo agotado", request=request)

    with pytest.raises(DescargaFallida):
        await extraer("https://example.com/", CONFIG, transporte(manejador))


def test_validar_url_normaliza_nombre():
    destino = validar_url("HTTPS://Example.COM./ruta")
    assert destino.host == "example.com"
    assert destino.puerto == 443


@pytest.mark.parametrize("url", ["", "https://", "https://usuario:clave@example.com/", "nada"])
def test_validar_url_invalida(url):
    with pytest.raises((UrlInvalida, SitioNoPermitido)):
        validar_url(url)


def test_url_con_credenciales_es_invalida():
    with pytest.raises(UrlInvalida):
        validar_url("https://usuario:clave@example.com/")


def test_configuracion_desde_entorno():
    config = Configuracion.desde_entorno(
        {
            "TIMEOUT_S": "3",
            "MAX_BYTES": "10",
            "USER_AGENT": "x",
        }
    )
    assert (config.timeout_s, config.max_bytes, config.user_agent) == (3.0, 10, "x")


def test_valores_por_omision():
    config = Configuracion.desde_entorno({})
    assert (config.timeout_s, config.max_bytes) == (10.0, 2_000_000)
    assert {80, 443} == egreso.PUERTOS_PERMITIDOS
