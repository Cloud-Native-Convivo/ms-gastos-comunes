# Build multi-stage: compila con Maven + JDK 21, corre con solo JRE 21.
# Usa la imagen oficial de Maven (el proyecto no incluye Maven Wrapper).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
# Cachea dependencias en su propia capa (solo se re-descargan si pom.xml cambia).
RUN mvn -B dependency:go-offline || true

COPY src src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /app/target/ms-gastos-comunes.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
