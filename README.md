# CLAMON

Plataforma para identificar y clasificar información sensible publicada en sitios web. Se compone de cuatro servicios independientes más MySQL y MinIO.

| Servicio | Tecnología | Función |
|---|---|---|
| `frontend` | nginx + HTML/JS | Inicia sesión, muestra las secciones según los roles y consume la API |
| `backend` | Java 21, Spring Boot 4.1 | Autentica, emite y valida tokens, autoriza por rol, registra sitios y orquesta los análisis |
| `clasificador` | Python 3.13, FastAPI | Recibe texto y devuelve los hallazgos de información sensible enmascarados |
| `scraper` | Python 3.13, FastAPI | Descarga la página de un sitio registrado y devuelve su texto |
| MySQL | 8.4 | `clamon_usuarios` (cuentas y bitácora) y `clamon_negocio` (sitios y análisis) |
| MinIO | S3 compatible | Bucket `expedientes` con el texto de cada análisis |

## Ejecutar

Todo el sistema se levanta con un solo comando:

```bash
docker compose up --build
```

| Servicio | URL |
|---|---|
| Frontend | `http://localhost:8081` |
| Backend | `http://localhost:8090` |
| Clasificador | `http://localhost:8091` |
| Scraper | `http://localhost:8092` |
| Consola de MinIO | `http://localhost:9001` |

Entra en `http://localhost:8081` con el administrador inicial `adan.admin` / `clamon-admin-2026`. El backend crea esa cuenta y las tablas al arrancar si no existen, y el bucket `expedientes` en MinIO; desde **Usuarios** se crean las demás cuentas.

## Variables de entorno

Cada servicio toma sus variables de su `.env.example` (versionado, con los valores locales por omisión) y, encima, de su `.env` si existe (ignorado por git, para cambios locales). `compose.yaml` las carga con `env_file`, así que no hace falta `.env` para clonar y levantar el proyecto; solo las URLs de red interna del backend se fijan en el compose, porque dentro de Docker los servicios se alcanzan por su nombre y no por `localhost`.

Los contenedores corren como `linux/amd64` y, salvo el proceso maestro de nginx, no se ejecutan como root.

## Pruebas

```bash
cd backend && ./gradlew test
cd clasificador && uv run pytest && uv run ruff check
cd scraper && uv run pytest && uv run ruff check
```
