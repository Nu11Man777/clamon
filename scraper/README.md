# scraper

Servicio FastAPI que descarga la página de un sitio permitido y devuelve su texto. Valida esquema, puerto, redirecciones dentro del mismo sitio, tipo de contenido y límites de tiempo y tamaño.

```bash
cp .env.example .env
uv run pytest && uv run ruff check
uv run --env-file .env scraper
```
