import json

import httpx
import pytest
from fastapi.testclient import TestClient

from scraper import registro
from scraper.main import app


@pytest.fixture
def cliente():
    def manejador(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, headers={"content-type": "text/html"}, content=b"<p>Hola</p>")

    app.state.transporte = httpx.MockTransport(manejador)
    yield TestClient(app)


def test_salud(cliente):
    assert cliente.get("/salud").json() == {"estado": "ok", "servicio": "clamon-scraper"}


def test_extraer(cliente):
    r = cliente.post("/extraer", json={"url": "https://example.com/"})
    assert r.status_code == 200
    assert r.json()["texto"] == "Hola"


def test_extraer_sitio_no_permitido_registra_evento(cliente, capsys):
    registro.configurar("clamon-scraper")
    r = cliente.post("/extraer", json={"url": "https://www.wikipedia.org:8443/"})
    assert r.status_code == 403
    assert r.json()["error"] == "sitio_no_permitido"
    eventos = [json.loads(linea) for linea in capsys.readouterr().out.splitlines() if linea]
    evento = next(e for e in eventos if e.get("evento") == "scraper.sitio_no_permitido")
    assert evento["host"] == "www.wikipedia.org"
    assert evento["motivo"] == "puerto"
    assert evento["severity"] == "WARNING"


@pytest.mark.parametrize("cuerpo", [{}, {"url": ""}, {"url": 5}, None])
def test_extraer_url_invalida(cliente, cuerpo):
    r = cliente.post("/extraer", json=cuerpo)
    assert r.status_code == 400
    assert r.json()["error"] == "url_invalida"
