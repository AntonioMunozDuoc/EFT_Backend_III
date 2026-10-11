# syntax=docker/dockerfile:1
# =============================================================================
# Imagen de bff-mobile
# Contexto de build: RAIZ del repositorio (el modulo depende del pom padre y de bank-common)
#   docker build -f bff-mobile/Dockerfile -t bancoxyz/bff-mobile .
# =============================================================================

# ---------- Etapa 1: compilacion ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl bff-mobile -am -DskipTests package

# ---------- Etapa 2: runtime liviano (solo JRE) ----------
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1001 --no-create-home app
WORKDIR /app
COPY --from=build /workspace/bff-mobile/target/bff-mobile-*.jar app.jar

USER app
EXPOSE 8082

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
