# frontend

Página estática servida por nginx. Inicia sesión contra el backend, muestra las secciones según los roles del usuario y consume la API. CSP estricta, sin código en línea.

```bash
cp .env.example .env
docker build -t clamon-frontend:local .
docker run --rm -p 8081:8080 --env-file .env clamon-frontend:local
```
