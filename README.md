# ms-gastos-comunes

Microservicio de dominio **Gastos Comunes** de Convivo: cobros/cuotas y
pagos por unidad. Java 25 + Spring Boot 4 + Oracle, detrás del BFF
(`GASTOS_COMUNES_URL`, puerto **8083**) y consumidor asíncrono de eventos
de reservas de espacios comunes vía RabbitMQ (patrón Outbox/Inbox).

## Cómo encaja en la arquitectura Convivo v2.7

```
Frontend (React/Angular) -> AWS API Gateway -> BFF (NestJS) -> ms-gastos-comunes -> Oracle (gastos_db)
                                                                        ^
                                                                        |
                                              RabbitMQ (espacios_events) — evento reserva_espacio_creada
                                                                        ^
                                                              ms-espacios-comunes (Outbox)
```

Este repo implementa exactamente los dos diagramas de secuencia que
definieron el alcance:

1. **Nivel 3 de validación JWT** — el BFF valida el token de forma
   completa (Nivel 2) y reenvía la petición con `Authorization`,
   `X-Usuario-Sub` y `X-Usuario-Roles`. Este microservicio **no confía en
   esos headers para autorizar**: vuelve a validar el JWT de forma
   autónoma (firma RS256 contra JWKS de Entra, issuer, audience —
   `SecurityConfig`) y deriva roles/identidad del token ya verificado
   (`JwtRolesConverter`, `IdentityContextFilter`). Los headers del BFF
   solo se usan para logging/detección de inconsistencias.
2. **Outbox/Inbox + saga coreografiada** — consume
   `reserva_espacio_creada` desde `gastos_reserva_creada_queue`
   (idempotente vía tabla `inbox_eventos`), crea el gasto común
   correspondiente, y ante datos inválidos publica `gasto_fallido` (vía
   su propio Outbox) hacia la cola `gasto_fallido` para que
   ms-espacios-comunes compense la reserva. Fallos técnicos persistentes
   terminan en `gastos_reserva_creada_dlq` (Dead Letter Queue) tras
   reintentos con backoff — ver `RabbitMqConfig`.

## Estructura

```
config/      SecurityConfig, RabbitMqConfig, EntraProperties, MensajeriaProperties
security/    JwtRolesConverter, IdentityContextFilter, UsuarioContexto (RBAC + ownership)
domain/      GastoComun, Pago y sus enums
repository/  Spring Data JPA
dto/         Request/Response
service/     GastoComunService (reglas de negocio + ownership)
web/         GastoComunController (API REST)
exception/   Errores uniformes (mismo formato que el filtro global del BFF)
messaging/
  outbox/    OutboxEvento, OutboxPublisherService, OutboxRelayScheduler
  inbox/     InboxEvento, ReservaEspacioCreadaListener, ReservaCreadaInboxService
```

## API REST (consumida solo por el BFF, nunca directo por los frontends)

| Método | Ruta | Roles | Notas |
|---|---|---|---|
| `GET`  | `/api/v1/gastos-comunes` | admin, comité, propietario, residente | admin/comité ven todo; el resto, solo su unidad (claim `unidad_id`) |
| `GET`  | `/api/v1/gastos-comunes/unidad/{unidadId}` | ídem | 403 si la unidad no es la propia y no es gestor |
| `GET`  | `/api/v1/gastos-comunes/{id}` | ídem | ownership por unidad del gasto |
| `POST` | `/api/v1/gastos-comunes` | **solo** admin, comité | alta manual de cobro/cuota |
| `POST` | `/api/v1/gastos-comunes/{id}/pagos` | ídem que GET | registra un abono/pago |
| `GET`  | `/api/v1/gastos-comunes/{id}/pagos` | ídem que GET | historial de pagos |

`conserje` no tiene acceso a este dominio. Errores en formato uniforme:

```json
{ "statusCode": 403, "code": "FORBIDDEN", "message": "...", "requestId": "..." }
```

Swagger UI: `/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`.

## Ejecutar en local (perfil `local`, por defecto)

No requiere Oracle ni RabbitMQ reales: usa H2 en memoria y el listener de
RabbitMQ arranca desactivado (`RABBITMQ_AUTOSTART=false`) para que el
servicio levante igual sin un broker corriendo.

```bash
mvn spring-boot:run
# o
mvn clean package && java -jar target/ms-gastos-comunes.jar
```

Health check: `curl http://localhost:8083/actuator/health`

Si quieres probar el consumo real de eventos en local, levanta un
RabbitMQ (`docker run -p 5672:5672 -p 15672:15672 rabbitmq:3-management`)
y define `RABBITMQ_AUTOSTART=true`.

## Ejecutar contra AWS (perfil `aws`)

```bash
export SPRING_PROFILES_ACTIVE=aws
# + todas las variables de .env.example (DB_URL, RABBITMQ_URLS, ENTRA_*, etc.)
java -jar target/ms-gastos-comunes.jar
```

En este perfil: Flyway corre las migraciones de `src/main/resources/db/migration`
sobre Oracle (`ddl-auto=validate`, nunca autogenera esquema en producción),
el listener de RabbitMQ arranca automáticamente, y Eureka/Config Server se
intentan usar pero de forma **tolerante** — igual que el BFF con
RabbitMQ: si `CONFIG_SERVER_URL` no responde, el arranque continúa
(`optional:configserver:`) en vez de fallar.

## Ejecutar con Docker (Oracle real en contenedor)

```bash
docker compose up --build
```

Levanta un Oracle Database Free 23ai dedicado (`oracle-gastos`, puerto
**1522** en el host — 1521 lo usa `oracle-espacios` si corre a la vez) y
el microservicio en perfil `aws` contra esa base real (Flyway corre la
migración inicial automáticamente). Verificado extremo a extremo: la
imagen compila con el Maven Wrapper (mismo patrón que
`config-server-cloud`/`discovery-server-cloud`), Hikari conecta a
Oracle, Flyway aplica `V1__init.sql` y el servicio responde en `:8083`.

**RabbitMQ no se levanta en este `docker-compose.yml` a propósito**: es
el mismo broker que consume/publica `ms-espacios-comunes` (saga
`reserva_espacio_creada` / `gasto_fallido`), así que lo trae el compose
de ese servicio en la misma red compartida `convivo-network` — acá solo
se referencia por nombre (`rabbitmq:5672`). Si corres este servicio solo,
el listener queda reintentando sin bloquear el arranque (mismo criterio
tolerante que documenta la sección de arriba).

`EUREKA_URL`/`CONFIG_SERVER_URL` apuntan a `discovery-server`/`config-server`
en esa misma red — levántalos con sus propios `docker compose up` (o
usa `EUREKA_ENABLED=false` si solo quieres probar Oracle).

## Tests

```bash
mvn test
```

Incluye: mapeo de roles desde el JWT (`JwtRolesConverterTest`), las tres
ramas del patrón Inbox — duplicado / inválido+compensación / procesado
(`ReservaCreadaInboxServiceTest`), el relay de Outbox con éxito y fallo de
publicación (`OutboxRelaySchedulerTest`), y la API REST con JWT simulado
cubriendo ownership por unidad y autorización por rol
(`GastoComunControllerTest`).

> Nota: este proyecto se generó sin acceso a Maven Central para verificar
> versiones de dependencias en vivo. Antes del primer build revisa
> especialmente `ojdbc11.version` en `pom.xml` (comentario con el enlace a
> Maven Central) y corre `mvn versions:display-dependency-updates` si
> quieres confirmar que todo está en su última versión estable.

## Cambios relacionados en el BFF

Para que este microservicio pueda leer `X-Usuario-Sub` / `X-Usuario-Roles`
(hoy solo usados para logging/cross-check, ver más abajo) y para que
`GASTOS_COMUNES_URL` apunte al puerto correcto, se actualizó el BFF en
paralelo a este trabajo:

- `proxy/gastos-proxy.controller.ts` y `proxy/proxy.service.ts`: ahora
  reenvían `X-Usuario-Sub` (claim `oid`) y `X-Usuario-Roles` junto con
  `Authorization`.
- `.env.example`: `GASTOS_COMUNES_URL` corregido de `:8081` a `:8083`.
- `common/interfaces/usuario-autenticado.ts` e `identity-mapper.ts`: el
  tipo `Rol` y el filtro de roles solo incluían
  `administrador | conserje | comite`, lo que descartaba silenciosamente
  a `propietario` y `residente` — los roles que precisamente necesitan
  consultar sus propios gastos comunes. Se agregaron ambos roles.
  Registrar pagos (`POST /{id}/pagos`) queda solo para
  `administrador`/`comite`: un pago autodeclarado por el residente
  saldaría su propia deuda sin verificación externa.

Esos archivos del BFF se entregan junto con este microservicio.

## Decisiones y limitaciones conocidas (para la siguiente iteración)

- **Ownership por `unidad_id`**: el claim `unidad_id` de propietario/
  residente todavía no está definido en Entra (mismo estado que documentan
  las notas del BFF para el claim de rol). Mientras no exista, esos roles
  reciben 403 con un mensaje explícito en vez de fallar de forma confusa.
- **Validación de negocio del evento de reserva**: sin una réplica local
  ni llamada síncrona a MS-USUARIOS, la validación de "unidad existe" /
  "residente no bloqueado" se limita a comprobar que el evento traiga los
  campos requeridos (`unidadId`, `usuarioSub`, `monto` > 0). Una validación
  más profunda requeriría que el evento la traiga ya resuelta desde el
  productor, o una consulta a un servicio de unidades.
- **Reportes / cobranza**: fuera de alcance de esta iteración (el panel de
  administración ya los marca como "Próximamente"); el modelo de datos
  (`estado`, `saldo_pendiente`) está pensado para soportarlos después sin
  romper compatibilidad.

## Prevención de fallos en CI y protocolo de PRs

Para evitar que los pipelines y checks automáticos fallen en pull requests, el proyecto cuenta con mecanismos nativos de prevención:

### 1. Verificación local pre-push (100% Maven / Java)
Antes de abrir un PR o hacer push a `develop`, corre:
```bash
./mvnw clean verify
```
(En Windows PowerShell/CMD: `.\mvnw.cmd clean verify`).

Esto valida de forma nativa:
- **Compilación estricta y tests**: ejecuta los 114 tests unitarios y de integración.
- **Enforcer de entorno**: `maven-enforcer-plugin` valida JDK 25 y Apache Maven >= 3.9.0.
- **Cobertura de código**: `jacoco-maven-plugin:check` exige un mínimo del **60%** de cobertura en líneas. Si agregas nueva lógica sin tests suficientes, el comando falla localmente antes de subir cambios a GitHub.
- **Validador de mensajes de commit**: `GitCommitConventionTest` valida el formato Gitmoji y Conventional Commits. Puedes verificar el mensaje del último commit (`HEAD`) con:
  ```bash
  ./mvnw test -Dtest=GitCommitConventionTest -Dcheck.commit=true
  ```

### 2. Formato estricto de títulos de PR (Gitmoji)
El workflow `pr-quality.yml` valida que los títulos de los PRs comiencen con Gitmoji (shortcode o unicode) y sigan Conventional Commits (`^(?::\w+:|[^\w\s:]\S*)\s(\w*)(?:\(([\w$.\-*/ ]*)\))?!?:\s(.*)$`).

Ejemplos válidos:
- `:sparkles: feat: nuevo prorrateo de reserva` o `✨ feat: nuevo prorrateo de reserva`
- `:bug: fix(cobros): corregir cálculo de intereses` o `🐛 fix(cobros): corregir cálculo de intereses`
- `:lock: fix(deps): mitigar cve crítico`
- `:memo: docs: actualizar runbook de despliegue`

### 3. Excepciones de seguridad temporales (`.trivyignore`)
El job `docker` en `ci.yml` ejecuta Trivy y falla ante vulnerabilidades `CRITICAL`. Si una vulnerabilidad pertenece a la imagen base de Temurin/Alpine y no existe parche upstream disponible de forma inmediata:
1. Registra el CVE en `.trivyignore` siguiendo el formato:
   ```text
   CVE-YYYY-XXXXX # [EXPIRACION YYYY-MM-DD] [JUSTIFICACION: motivo tecnico]
   ```
2. Toda excepción requiere fecha de expiración y justificación técnica.

### 4. Automatizaciones en GitHub Actions
- **Caché Docker Buildx (GHA)**: `ci.yml` utiliza `cache-from: type=gha` y `cache-to: type=gha,mode=max` para acelerar builds en runners de GitHub.
- **Dependabot Auto-Merge**: `dependabot-auto.yml` aprueba y mergea automáticamente con squash los PRs de parches y versiones menores seguros de Dependabot una vez que todos los checks de CI pasen en verde.
- **Bloqueo de versiones mayores**: `.github/dependabot.yml` ignora `semver-major` en `spring-boot`, `spring-cloud` y `springdoc` para evitar PRs aislados que quiebran la compilación.

