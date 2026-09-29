# ============================================
# Etapa 1: Descargar dependencias (cache)
# ============================================
FROM eclipse-temurin:24-jdk-alpine@sha256:8fdbcb6bc6b846640cea7058e6eeb56c311fae4efaa506a213789134065c6b90 AS deps
WORKDIR /app
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B dependency:go-offline

# ============================================
# Etapa 2: Compilar la aplicación
# ============================================
FROM deps AS build
COPY src ./src
RUN ./mvnw -B package -DskipTests

# ============================================
# Etapa 3: Imagen final liviana
# ============================================
FROM eclipse-temurin:24-jre-alpine@sha256:4044b6c87cb088885bcd0220f7dc7a8a4aab76577605fa471945d2e98270741f
# apk upgrade: la imagen base arrastra gnutls/libcrypto3 con CVEs CRITICAL ya parchados en Alpine
RUN apk upgrade --no-cache && apk add --no-cache curl

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/ms-gastos-comunes.jar app.jar

EXPOSE 8083

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Verifica que Spring Boot esté vivo
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]