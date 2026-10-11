# syntax=docker/dockerfile:1
# =============================================================================
# Imagen de discovery-server
# Contexto de build: RAIZ del repositorio (el modulo depende del pom padre y de bank-common)
#   docker build -f discovery-server/Dockerfile -t bancoxyz/discovery-server .
# =============================================================================

# ---------- Etapa 1: compilacion ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl discovery-server -am -DskipTests package

# ---------- Etapa 2: runtime liviano (solo JRE) ----------
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1001 --no-create-home app
WORKDIR /app
COPY --from=build /workspace/discovery-server/target/discovery-server-*.jar app.jar

USER app
EXPOSE 8761

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD curl -fsS http://localhost:8761/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
