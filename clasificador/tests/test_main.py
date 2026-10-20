import json
import logging

from fastapi.testclient import TestClient

from clasificador import registro
from clasificador.main import app

cliente = TestClient(app)


def test_salud():
    r = cliente.get("/salud")
    assert r.status_code == 200
    assert r.json() == {"estado": "ok", "servicio": "clamon-clasificador"}


def test_clasificar():
    r = cliente.post(
        "/clasificar", json={"texto": "Contacto: juan.perez@ejemplo.mx, tel 55 1234 4567"}
    )
    assert r.status_code == 200
    assert r.json()["nivel"] == "medio"
    assert r.json()["total"] == 2


def test_clasificar_texto_vacio():
    r = cliente.post("/clasificar", json={"texto": ""})
    assert r.status_code == 422
    assert r.json()["error"] == "validacion"


def test_clasificar_texto_excede_limite_no_devuelve_el_texto():
    texto = "secreto " * 3000
    r = cliente.post("/clasificar", json={"texto": texto})
    assert r.status_code == 422
    assert "secreto" not in r.text


def test_clasificar_sin_cuerpo():
    assert cliente.post("/clasificar").status_code == 422


def test_ruta_inexistente_responde_json():
    r = cliente.get("/nada")
    assert r.status_code == 404
    assert r.json()["error"] == "no_encontrado"


def test_registro_no_contiene_el_texto(capsys):
    registro.configurar("clamon-clasificador")
    cliente.post("/clasificar", json={"texto": "correo juan.perez@ejemplo.mx"})
    lineas = [json.loads(linea) for linea in capsys.readouterr().out.splitlines() if linea]
    evento = next(linea for linea in lineas if linea.get("evento") == "clasificador.clasificacion")
    assert evento["servicio"] == "clamon-clasificador"
    assert evento["severity"] == "INFO"
    assert "juan" not in json.dumps(evento)
    logging.getLogger().handlers.clear()


def test_extraer_traza():
    tp = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
    assert registro.extraer_traza(tp, None) == (
        "4bf92f3577b34da6a3ce929d0e0e4736",
        "00f067aa0ba902b7",
    )
    assert registro.extraer_traza(None, "4bf92f3577b34da6a3ce929d0e0e4736/1;o=1") == (
        "4bf92f3577b34da6a3ce929d0e0e4736",
        "0000000000000001",
    )
    assert registro.extraer_traza("basura", None) is None
