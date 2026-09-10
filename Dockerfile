# ============================================
# Etapa 1: Descargar dependencias (cache)
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS deps
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
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
