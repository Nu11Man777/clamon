# clasificador

Servicio FastAPI que recibe texto y devuelve los hallazgos de información sensible enmascarados (correo, teléfono, CURP, RFC, tarjeta e IPv4), con un nivel de riesgo agregado.

```bash
cp .env.example .env
uv run pytest && uv run ruff check
uv run --env-file .env clasificador
```
