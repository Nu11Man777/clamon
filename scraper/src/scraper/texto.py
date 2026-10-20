from __future__ import annotations

from html.parser import HTMLParser

MAX_CARACTERES = 20_000

_OMITIR = frozenset({"script", "style", "noscript", "template", "svg", "iframe", "object"})


class _ExtractorTexto(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._partes: list[str] = []
        self._omitiendo = 0

    def handle_starttag(self, tag: str, attrs: list) -> None:
        if tag in _OMITIR:
            self._omitiendo += 1

    def handle_endtag(self, tag: str) -> None:
        if tag in _OMITIR and self._omitiendo:
            self._omitiendo -= 1

    def handle_data(self, data: str) -> None:
        if not self._omitiendo:
            self._partes.append(data)

    def texto(self) -> str:
        return " ".join(" ".join(self._partes).split())


def html_a_texto(html: str) -> str:
    extractor = _ExtractorTexto()
    extractor.feed(html)
    extractor.close()
    return extractor.texto()


def normalizar_texto_plano(texto: str) -> str:
    return " ".join(texto.split())


def recortar(texto: str) -> tuple[str, bool]:
    if len(texto) > MAX_CARACTERES:
        return texto[:MAX_CARACTERES], True
    return texto, False
