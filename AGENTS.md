# AGENTS.md — ms-gastos-comunes

`AGENTS.md` es un formato abierto: un Markdown en la raíz del repositorio
que los agentes de código leen antes de actuar. Este archivo sigue la
misma convención que `ms-espacios-comunes/AGENTS.md` (mismo proyecto
Convivo), adaptada al stack Java/Spring Boot de este microservicio.

## 0. Jerarquía de reglas

1. Seguridad y corrección — nunca se sacrifican por ninguna otra regla.
2. Convenciones del proyecto (stack, estilo, arquitectura) — se siguen
   salvo instrucción explícita en contrario.
3. Minimalismo (sección 6, disciplina Ponytail) — se aplica solo después
   de satisfacer 1 y 2.

## 1. Resumen del proyecto

`ms-gastos-comunes` es el microservicio del dominio **Gastos Comunes** de
Convivo (plataforma de gestión de condominios en Chile): cobros, cuotas y
pagos por unidad. Se ubica detrás del BFF (NestJS) y consume eventos de
`ms-espacios-comunes` vía RabbitMQ (patrón Outbox/Inbox) para crear
gastos automáticamente al confirmarse una reserva.

Roles: `administrador` y `comite` gestionan cobros de todo el condominio;
`propietario` y `residente` solo consultan/pagan los gastos de su propia
unidad (`unidad_id`); `conserje` no tiene acceso a este dominio. El BFF
valida el JWT (Nivel 2) y reenvía `Authorization` + headers de identidad,
pero este servicio **no confía en esos headers para autorizar**: vuelve a
validar el JWT de forma autónoma (RS256 contra JWKS de Entra) — Nivel 3,
defensa en profundidad. Ver `SecurityConfig` y `JwtRolesConverter`.

## 2. Stack técnico

- Lenguaje: Java 21
- Framework: Spring Boot 3.3.4 + Spring Cloud 2023.0.3
- Persistencia: Spring Data JPA + Oracle Database (`ojdbc11`), Flyway
  para migraciones (`ddl-auto: validate` en `aws`, nunca autogenera
  esquema en producción)
- Mensajería: Spring AMQP (RabbitMQ / Amazon MQ), patrón Outbox/Inbox,
  `spring-retry` para reintentos con backoff
- Seguridad: Spring Security + OAuth2 Resource Server (JWT RS256 contra
  JWKS de Microsoft Entra ID)
- Descubrimiento/Config: `spring-cloud-starter-netflix-eureka-client` +
  `spring-cloud-starter-config` — ambos opcionales/tolerantes a
  no-disponibilidad (`eureka.client.enabled=false` en local;
  `optional:configserver:` en `aws`)
- Documentación: springdoc-openapi (`/swagger-ui.html`, `/v3/api-docs`)
- Tests: JUnit 5 + spring-boot-starter-test, spring-security-test,
  spring-rabbit-test
- Contenedores: Docker (perfil `local` usa H2 en memoria, sin
  dependencias externas)

## 3. Estructura del proyecto

```text
src/main/java/com/convivo/gastoscomunes/
  GastosComunesApplication.java
  config/
    EntraProperties.java        # Config JWT (issuer, jwks-uri, audience, claim-de-rol)
    MensajeriaProperties.java   # Config RabbitMQ (exchanges, colas, DLX, outbox)
    RabbitMqConfig.java         # Topología Outbox/Inbox/DLQ
    SecurityConfig.java         # OAuth2 Resource Server + PreAuthorize
    JsonAuthEntryPoints.java    # Errores 401/403 en formato uniforme
  domain/
    GastoComun.java, Pago.java, EstadoGasto.java, MetodoPago.java,
    OrigenGasto.java, RolConvivo.java
  dto/
    GastoComunRequest/Response.java, PagoRequest/Response.java
  exception/
    GlobalExceptionHandler.java, ErrorResponse.java,
    RecursoNoEncontradoException.java, OperacionNoPermitidaException.java
  messaging/
    outbox/  OutboxEvento, OutboxPublisherService, OutboxRelayScheduler
    inbox/   InboxEvento, ReservaEspacioCreadaListener, ReservaCreadaInboxService
  repository/
    GastoComunRepository.java, PagoRepository.java
  security/
    JwtRolesConverter.java      # Claim de rol -> GrantedAuthority ROLE_*
    IdentityContextFilter.java  # Detecta inconsistencia header BFF vs JWT
    UsuarioContexto.java        # Identidad + rol resueltos del request actual
  service/
    GastoComunService.java      # Reglas de negocio + ownership por unidad
  web/
    GastoComunController.java   # API REST bajo /api/v1/gastos-comunes
src/main/resources/
  application.yml, application-local.yml, application-aws.yml
  db/migration/                 # Flyway
src/test/java/com/convivo/gastoscomunes/  # espejo del código fuente
```

## 4. Comandos

```bash
# levantar local (perfil local por defecto, H2 en memoria, sin Oracle/RabbitMQ reales)
./mvnw spring-boot:run

# test completo
./mvnw test

# build
./mvnw clean package

# verificar build Docker
docker build -t ms-gastos-comunes .

# health check
curl http://localhost:8083/actuator/health
```

## 5. Estilo de código

**Todo en español**: nombres de clases (`GastoComunService`), métodos
(`registrarPago`, `listarPorUnidad`), variables (`unidadId`, `montoPago`),
mensajes de error. Términos del dominio (`GastoComun`, `Pago`, `Outbox`,
`Inbox`) y anotaciones/keywords de Java quedan en su idioma original.

Patrones obligatorios:

- Records/DTOs inmutables para request/response.
- `Optional<T>` en vez de `null` en retornos de repositorio cuando
  corresponda; nunca `Optional` como parámetro.
- Queries vía Spring Data JPA (derivadas o `@Query` parametrizada) —
  nunca concatenar input de usuario.
- Excepciones de negocio propias (`RecursoNoEncontradoException`,
  `OperacionNoPermitidaException`) capturadas por
  `GlobalExceptionHandler`, nunca stack trace crudo al cliente.
- Javadoc en español en clases públicas de `service/`, `web/` y
  `messaging/` (qué hace, no cómo).

## 6. Disciplina anti-sobreingeniería (Ponytail)

(Se conserva aunque el agente principal ya traiga esta disciplina por
configuración global: este archivo también lo leen agentes que no cargan
esa configuración. Si las dos divergen, para ese agente manda la global.)

Escalera de decisión antes de escribir código nuevo:

1. ¿Es necesario construir esto? (YAGNI)
2. ¿La librería estándar o Spring ya lo resuelve? Úsala.
3. ¿Una dependencia ya instalada lo resuelve? Úsala.
4. ¿Se puede resolver en una línea? Hazlo en una línea.
5. Solo entonces: escribe el mínimo código funcional.

No aplicar pereza en: comprensión completa del problema, validación de
inputs en fronteras de confianza, manejo de errores que previene pérdida
de datos, seguridad, y cualquier cosa explícitamente solicitada.

Toda lógica no trivial deja una verificación ejecutable mínima (test
JUnit) — ver umbral real en sección 7.

## 7. Pruebas

Framework: JUnit 5. Ubicación: `src/test/java/...` con estructura espejo
del código fuente.

Tests existentes: mapeo de roles desde el JWT (`JwtRolesConverterTest`),
las tres ramas del patrón Inbox — duplicado / inválido+compensación /
procesado (`ReservaCreadaInboxServiceTest`), el relay de Outbox con éxito
y fallo de publicación (`OutboxRelaySchedulerTest`), y la API REST con
JWT simulado cubriendo ownership por unidad y autorización por rol
(`GastoComunControllerTest`).

Cubrir camino feliz, camino de error y casos límite (overlap de estado
del gasto, montos inválidos, ownership cruzado entre unidades). Un test
inestable (*flaky*) es un test roto: se arregla de inmediato, nunca se
ignora reintentando.

## 8. Métricas de claridad

| Métrica | Umbral |
| --- | --- |
| Longitud de método | ≤ 40 líneas |
| Nesting | ≤ 3 niveles |
| Javadoc | obligatorio en `service/`, `web/`, `messaging/` públicos |

## 9. Procedimientos QA

Checklist pre-entrega: `./mvnw test` en verde, sin secrets hardcodeados,
queries parametrizadas (Spring Data JPA), `GlobalExceptionHandler` sin
fugas de stack trace, documentación (README/AGENTS.md) actualizada.

| Severidad | Acción |
| --- | --- |
| Crítico | bloquea el merge |
| Mayor | corregir antes del merge salvo excepción documentada |
| Menor | issue de seguimiento post-merge |

## 10. Seguridad

Secretos en variables de entorno (`.env` local / AWS Secrets Manager en
producción). Nunca en código, logs ni en `config-server`.

- **Autenticación**: delegada a Microsoft Entra ID; este servicio valida
  el JWT de forma autónoma (RS256, `SecurityConfig` + `JwtRolesConverter`)
  en vez de confiar en los headers `X-Usuario-*` del BFF. `IdentityContextFilter`
  registra (solo log) si el header del BFF y el JWT difieren.
- **Autorización**: `@PreAuthorize` con roles de `RolConvivo`; ownership
  por unidad resuelto en `GastoComunService`, nunca confiando en el
  cliente.
- **Inyección**: Spring Data JPA parametriza siempre; nunca concatenar
  input de usuario en queries nativas.
- **Inconsistencias conocidas**: el claim `unidad_id` de
  propietario/residente todavía no está definido en Entra — ver
  limitación documentada en `README.md`.

Antes de mergear cambios con superficie de seguridad (auth, input
externo, permisos, mensajería), correr el skill/agente de revisión de
seguridad disponible — no depender solo de revisión manual.

## 11. Commits y PR

Conventional Commits v1.0.0 + Gitmoji (mismo estándar que
`ms-espacios-comunes/AGENTS.md` §11 — ver ese archivo para el detalle
completo de reglas MUST, alcance de commit y modelo de ramas Git Flow;
no se repite acá para evitar que las dos copias diverjan).

Alcance de este repo: `gastos`, `pagos`, `outbox`, `inbox`, `api`, `db`,
`docker`, `deps`, `ci`.

Nunca agregar trailers/firmas de autoría de agente/IA a un commit ni a un
PR, salvo pedido explícito del usuario para ese commit/PR puntual.

## 12. Límites del agente

**Siempre** (sin pedir permiso): editar código, tests, docs dentro del
repo; crear commits locales.

**Preguntar primero**: force-push, `git reset --hard`/`clean`, agregar o
actualizar dependencias, cualquier acción que afecte estado compartido
(push, PR, deploy a staging).

**Nunca sin aprobación explícita**: migraciones aplicadas en Oracle real,
configuración de CI/CD, archivos de secretos/`.env`, deploy a producción,
reescritura de historial publicado, borrado de datos o infraestructura,
comunicación hacia afuera del repositorio.

## 13. Deploy

```bash
# local
./mvnw spring-boot:run

# producción (ECS Fargate) — perfil aws, todas las variables de .env.example
export SPRING_PROFILES_ACTIVE=aws
java -jar target/ms-gastos-comunes.jar
```

## 14. Monorepo

`(no aplica: microservicio independiente dentro del workspace Convivo,
mismo criterio que ms-espacios-comunes/AGENTS.md §14)`.

## 15. Enforcement

Este archivo es orientativo, no mecánicamente forzado. Las reglas
críticas (secretos, queries parametrizadas, autorización) deben
reforzarse con CI, no depender solo de este texto — pendiente: este
repo todavía no tiene workflow de CI (`docker-publish.yml`) como
`config-server-cloud`/`discovery-server-cloud`.

## 16. Mantenimiento

Tratar como código. Symlinkear en vez de duplicar si otra herramienta
requiere su propio archivo de reglas. Para el detalle normativo completo
(ISO/IEC 25010, 27001, Ley 21.719 y su cruce con el resto de normativa
chilena) ver `ms-espacios-comunes/AGENTS.md` §17 — aplica al mismo
proyecto Convivo y no se duplica acá.
