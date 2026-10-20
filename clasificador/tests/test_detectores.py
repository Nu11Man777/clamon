import pytest

from clasificador.detectores import calcular_nivel, clasificar, detectar, luhn_valido


def tipos(texto: str) -> list[str]:
    return [h.tipo for h in detectar(texto)]


def test_correo_positivo_enmascara_usuario():
    texto = "Contacto: juan.perez@ejemplo.mx, gracias."
    [h] = detectar(texto)
    assert h.tipo == "correo"
    assert h.valor == "j****@ejemplo.mx"
    assert texto[h.inicio : h.fin] == "juan.perez@ejemplo.mx"


def test_correo_negativo():
    assert "correo" not in tipos("escribe a juan arroba ejemplo punto mx o a juan@ sin dominio")


@pytest.mark.parametrize(
    "texto",
    ["tel 55 1234 4567", "tel 5512344567", "tel +52 55-1234-4567", "tel 55.1234.4567"],
)
def test_telefono_positivo_enmascara_ultimos_cuatro(texto):
    [h] = detectar(texto)
    assert h.tipo == "telefono_mx"
    assert h.valor == "******4567"


@pytest.mark.parametrize("texto", ["folio 123456789", "folio 55123445671", "cp 07738"])
def test_telefono_negativo(texto):
    assert "telefono_mx" not in tipos(texto)


def test_curp_positivo_enmascara_primeros_cuatro():
    [h] = detectar("CURP GOMR800101HDFRRN09.")
    assert h.tipo == "curp"
    assert h.valor == "GOMR**************"
    assert len(h.valor) == 18


def test_curp_negativo_sexo_invalido():
    assert "curp" not in tipos("GOMR800101XDFRRN09")


@pytest.mark.parametrize(
    ("texto", "valor"),
    [
        ("RFC GOMR800101AB1.", "GOMR*********"),
        ("RFC empresa ABC010203XY9", "ABC0********"),
        ("RFC MUÑO800101AB1", "MUÑO*********"),
    ],
)
def test_rfc_positivo(texto, valor):
    [h] = detectar(texto)
    assert h.tipo == "rfc"
    assert h.valor == valor


def test_rfc_negativo():
    assert "rfc" not in tipos("ABC12345XYZ y abcd800101ab1")


def test_rfc_dentro_de_curp_se_descarta():
    hallazgos = detectar("CURP GOMR800101HDFRRN09")
    assert [h.tipo for h in hallazgos] == ["curp"]


@pytest.mark.parametrize(
    ("texto", "valor"),
    [
        ("tarjeta 4111 1111 1111 1111", "************1111"),
        ("tarjeta 5555-5555-5555-4444", "************4444"),
        ("tarjeta 378282246310005", "***********0005"),
    ],
)
def test_tarjeta_positivo_enmascara_ultimos_cuatro(texto, valor):
    [h] = detectar(texto)
    assert h.tipo == "tarjeta"
    assert h.valor == valor


@pytest.mark.parametrize(
    "texto",
    [
        "tarjeta 4111 1111 1111 1112",
        "pedido 411111111111",
        "serie 41111111111111111111",
    ],
)
def test_tarjeta_negativo(texto):
    assert "tarjeta" not in tipos(texto)


def test_luhn():
    assert luhn_valido("4111111111111111")
    assert luhn_valido("79927398713")
    assert not luhn_valido("4111111111111112")
    assert not luhn_valido("79927398710")
    assert not luhn_valido("")
    assert not luhn_valido("4111-1111")


def test_ipv4_positivo_enmascara_primer_octeto():
    [h] = detectar("servidor 192.168.10.1.")
    assert h.tipo == "ipv4"
    assert h.valor == "192.*.*.*"


@pytest.mark.parametrize("texto", ["version 256.1.1.1", "version 1.2.3", "1.2.3.4.5"])
def test_ipv4_negativo(texto):
    assert "ipv4" not in tipos(texto)


def test_ipv4_de_diez_digitos_no_es_telefono():
    assert tipos("host 192.168.1.10") == ["ipv4"]


@pytest.mark.parametrize(
    ("presentes", "nivel"),
    [
        ({"curp"}, "alto"),
        ({"rfc", "correo"}, "alto"),
        ({"tarjeta"}, "alto"),
        ({"correo"}, "medio"),
        ({"telefono_mx", "ipv4"}, "medio"),
        ({"ipv4"}, "bajo"),
        (set(), "ninguno"),
    ],
)
def test_nivel(presentes, nivel):
    assert calcular_nivel(presentes) == nivel


def test_clasificar_resultado_completo():
    texto = "Contacto: juan.perez@ejemplo.mx, CURP GOMR800101HDFRRN09, tel 55 1234 4567"
    r = clasificar(texto)
    assert r["nivel"] == "alto"
    assert r["total"] == 3
    assert r["categorias"] == {"correo": 1, "curp": 1, "telefono_mx": 1}
    assert [h["tipo"] for h in r["hallazgos"]] == ["correo", "curp", "telefono_mx"]
    for h in r["hallazgos"]:
        assert set(h) == {"tipo", "valor", "inicio", "fin"}


def test_clasificar_sin_hallazgos():
    assert clasificar("texto sin datos sensibles") == {
        "nivel": "ninguno",
        "total": 0,
        "categorias": {},
        "hallazgos": [],
    }
