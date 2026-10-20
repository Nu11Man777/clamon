from __future__ import annotations

import re
from collections import Counter
from collections.abc import Callable
from dataclasses import asdict, dataclass

CORREO = "correo"
TELEFONO_MX = "telefono_mx"
CURP = "curp"
RFC = "rfc"
TARJETA = "tarjeta"
IPV4 = "ipv4"


@dataclass(frozen=True, slots=True)
class Hallazgo:
    tipo: str
    valor: str
    inicio: int
    fin: int


_CORREO = re.compile(
    r"(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}"
    r"(?![A-Za-z0-9-])"
)
_TELEFONO_MX = re.compile(r"(?<![\d+])(?<!\d[ .-])(?:\+52[ .-]?)?\d(?:[ .-]?\d){9}(?![ .-]?\d)")
_CURP = re.compile(r"(?<![A-Z\d])[A-Z][AEIOUX][A-Z]{2}\d{6}[HM][A-Z]{5}[A-Z\d]\d(?![A-Z\d])")
_RFC = re.compile(r"(?<![A-ZÑ&\d])[A-ZÑ&]{3,4}\d{6}[A-Z\d]{3}")
_TARJETA = re.compile(r"(?<!\d)(?<!\d[ -])\d(?:[ -]?\d){12,18}(?![ -]?\d)")
_OCTETO = r"(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)"
_IPV4 = re.compile(rf"(?<![\d.])(?:{_OCTETO}\.){{3}}{_OCTETO}(?!\d)(?!\.\d)")


def luhn_valido(digitos: str) -> bool:
    if not digitos or not all("0" <= c <= "9" for c in digitos):
        return False
    total = 0
    for i, c in enumerate(reversed(digitos)):
        d = int(c)
        if i % 2 == 1:
            d *= 2
            if d > 9:
                d -= 9
        total += d
    return total % 10 == 0


def _solo_digitos(valor: str) -> str:
    return "".join(c for c in valor if "0" <= c <= "9")


def _enmascarar_correo(valor: str) -> str:
    usuario, dominio = valor.split("@", 1)
    return f"{usuario[0]}****@{dominio}"


def _enmascarar_ultimos4(valor: str, relleno: int | None = None) -> str:
    digitos = _solo_digitos(valor)
    asteriscos = relleno if relleno is not None else len(digitos) - 4
    return "*" * asteriscos + digitos[-4:]


def _enmascarar_primeros4(valor: str) -> str:
    return valor[:4] + "*" * (len(valor) - 4)


def _enmascarar_ipv4(valor: str) -> str:
    return valor.split(".", 1)[0] + ".*.*.*"


@dataclass(frozen=True, slots=True)
class _Detector:
    tipo: str
    patron: re.Pattern[str]
    enmascarar: Callable[[str], str]
    validar: Callable[[str], bool] = lambda _valor: True


DETECTORES: tuple[_Detector, ...] = (
    _Detector(CORREO, _CORREO, _enmascarar_correo),
    _Detector(TARJETA, _TARJETA, _enmascarar_ultimos4, lambda v: luhn_valido(_solo_digitos(v))),
    _Detector(CURP, _CURP, _enmascarar_primeros4),
    _Detector(RFC, _RFC, _enmascarar_primeros4),
    _Detector(IPV4, _IPV4, _enmascarar_ipv4),
    _Detector(TELEFONO_MX, _TELEFONO_MX, lambda v: _enmascarar_ultimos4(v, relleno=6)),
)


def detectar(texto: str) -> list[Hallazgo]:
    aceptados: list[Hallazgo] = []
    for detector in DETECTORES:
        for m in detector.patron.finditer(texto):
            valor = m.group(0)
            if not detector.validar(valor):
                continue
            inicio, fin = m.span()
            if any(inicio < h.fin and h.inicio < fin for h in aceptados):
                continue
            aceptados.append(Hallazgo(detector.tipo, detector.enmascarar(valor), inicio, fin))
    return sorted(aceptados, key=lambda h: h.inicio)


def calcular_nivel(tipos: set[str]) -> str:
    if tipos & {CURP, RFC, TARJETA}:
        return "alto"
    if tipos & {CORREO, TELEFONO_MX}:
        return "medio"
    if IPV4 in tipos:
        return "bajo"
    return "ninguno"


def clasificar(texto: str) -> dict:
    hallazgos = detectar(texto)
    categorias = Counter(h.tipo for h in hallazgos)
    return {
        "nivel": calcular_nivel(set(categorias)),
        "total": len(hallazgos),
        "categorias": dict(sorted(categorias.items())),
        "hallazgos": [asdict(h) for h in hallazgos],
    }
