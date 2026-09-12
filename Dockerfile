# ============================================
# Etapa 1: Descargar dependencias (cache)
# ============================================
FROM eclipse-temurin:25-jdk-alpine AS deps
WORKDIR /app
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# ============================================
# Etapa 2: Compilar la aplicación
# ============================================
FROM deps AS build
COPY src ./src
RUN ./mvnw -B package -DskipTests

# ============================================
# Etapa 3: Imagen final liviana
# ============================================
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/ms-gastos-comunes.jar app.jar

EXPOSE 8083

# Verifica que Spring Boot esté vivo
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]