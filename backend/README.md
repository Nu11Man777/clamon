# clamon-backend

API en Spring Boot 4.1 y Java 21: autenticación, roles, sitios, análisis, diagnóstico y administración de cuentas.

```bash
cp .env.example .env
./gradlew test
./gradlew bootRun
```

## Estructura

Organizado por capas. Cada capa solo llama a la siguiente: controlador → servicio → repositorio o cliente.

```text
mx.ipn.cic.clamon
├── ClamonApplication           punto de entrada
├── config/                  configuración que se arma al arrancar
├── controller/              endpoints HTTP: reciben la petición y delegan al servicio
├── service/                 lógica de negocio
├── repository/              acceso a MySQL
├── client/                  servicios externos: clasificador, scraper y MinIO
│   └── dto/                 respuestas de clasificador y el scraper
├── model/                   datos del dominio, tal como se guardan
├── dto/
│   ├── request/             cuerpos que recibe la API
│   └── response/            cuerpos que devuelve la API
├── security/                autenticación, tokens, roles y política de cuentas
└── exception/               excepciones y su conversión a respuestas de error
```

| Paquete | Archivos |
|---|---|
| `config/` | `ClamonProperties`: variables de entorno. `SecurityConfig`: cadena de seguridad, rol por ruta, CORS, llave del token y BCrypt. `DataSourceConfig`: una conexión por base. `S3Config`: cliente de MinIO. `ClientesConfig`: clientes HTTP de clasificador y el scraper con sus tiempos de espera. `FormatoRegistro`: registros en JSON |
| `controller/` | `SesionController` (`/api/sesion`), `UsuarioController` (`/api/yo`), `AdministracionUsuariosController` (`/api/usuarios`), `SitioController` (`/api/sitios`), `AnalisisController` (`/api/analisis`), `ClasificacionController` (`/api/clasificacion`), `DiagnosticoController` (`/api/salud`, `/api/diagnostico`) |
| `service/` | `SesionService`: inicio de sesión, intentos y bloqueo. `UsuarioService`: consulta y administración de cuentas. `SitioService`: validación y registro de sitios. `AnalisisService`: orquesta scraper, clasificador, MySQL y MinIO. `ClasificacionService`: clasificación de texto. `DiagnosticoService`: estado de las dependencias. `EsquemaInicial`: tablas y administrador inicial al arrancar |
| `repository/` | `UsuarioRepository` y `BitacoraRepository` (base `clamon_usuarios`), `SitioRepository` y `AnalisisRepository` (base `clamon_negocio`) |
| `client/` | `ClasificadorClient`, `ScraperClient`, `AlmacenArchivos` (MinIO), `TokenIdentidadInterceptor` (token de Google en GCP), `ConsultaSalud` |
| `model/` | `Usuario`, `Sitio`, `Analisis`, `Hallazgo` |
| `dto/request/` | `PeticionSesion`, `PeticionCrearUsuario`, `PeticionModificarUsuario`, `PeticionSitio`, `PeticionAnalisis`, `PeticionClasificacion` |
| `dto/response/` | `Sesion`, `Yo`, `Cuenta`, `Salud`, `Diagnostico`, `Dependencia`, `RespuestaError` |
| `security/` | `RolesTokenConverter`: roles del token. `EmisorTokens`: firma del JWT. `RespuestasSeguridad`: 401 y 403. `FiltroRegistroSolicitudes`: registro de cada petición. `PoliticaUsuarios`: reglas de usuario y contraseña. `UsuarioActual`: nombre del usuario del token |
| `exception/` | `ErrorApi`: error con código HTTP. `SitioNoPermitido` y `ServicioNoDisponible`: errores de los clientes. `ManejadorErrores`: convierte cualquier excepción en `{"error","detalle"}` |

Las pruebas siguen los mismos paquetes.

## Recorrido de una petición

```text
Petición HTTP
  ├─ 1. Seguridad (config/SecurityConfig + security/)
  │     ├─ FiltroRegistroSolicitudes envuelve todo y al final registra api.solicitud
  │     ├─ Valida el token: firma, emisor, audiencia y vigencia; RolesTokenConverter lee los roles
  │     └─ Autoriza la ruta por rol; si falla, RespuestasSeguridad responde 401 o 403
  ├─ 2. controller/   convierte el JSON en un dto/request y llama al servicio
  ├─ 3. service/      aplica las reglas y coordina
  ├─ 4. repository/ y client/   MySQL, MinIO, clasificador y scraper
  └─ Si algo falla: exception/ManejadorErrores responde {"error","detalle"}
```

Ejemplo, `POST /api/analisis`:

1. La seguridad comprueba que el token tenga `ROLE_analista`.
2. `AnalisisController` recibe `PeticionAnalisis` y llama a `AnalisisService.analizar`.
3. `AnalisisService` coordina los pasos:
   - lee el sitio con `SitioRepository`;
   - pide la página a `ScraperClient`;
   - clasifica el texto con `ClasificadorClient`;
   - guarda el análisis con `AnalisisRepository` y el texto con `AlmacenArchivos`;
   - deja una entrada en la bitácora.
4. Responde `201` con el `Analisis`. Si el scraper rechaza el sitio, responde `422`; si una dependencia falla, `502`.

## Qué se ejecuta y cuándo

| Momento | Qué pasa |
|---|---|
| Al arrancar, una sola vez | Spring ejecuta `config/` y crea los objetos compartidos, sin conectarse a nada. Si MySQL, MinIO o Google no responden, la aplicación arranca igual |
| Al arrancar, en segundo plano | `EsquemaInicial` crea las tablas y `adan.admin`, y `AlmacenArchivos` crea el bucket. Cada uno reintenta cada 30 s en su propio hilo hasta lograrlo y termina |
| En la primera consulta | Se abren las conexiones a MySQL y, en GCP, se pide el token de identidad de Google, que después se renueva solo |
| En cada petición | Seguridad, controlador, servicio, repositorio o cliente |

No hay tareas programadas: fuera de esos dos hilos de arranque, nada corre si no llega una petición.
