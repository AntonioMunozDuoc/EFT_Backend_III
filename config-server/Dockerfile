# syntax=docker/dockerfile:1
# =============================================================================
# Imagen de config-server
# Contexto de build: RAIZ del repositorio (el modulo depende del pom padre y de bank-common)
#   docker build -f config-server/Dockerfile -t bancoxyz/config-server .
# =============================================================================

# ---------- Etapa 1: compilacion ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl config-server -am -DskipTests package

# ---------- Etapa 2: runtime liviano (solo JRE) ----------
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1001 --no-create-home app
WORKDIR /app
COPY --from=build /workspace/config-server/target/config-server-*.jar app.jar

# Repositorio de configuracion que sirve el Config Server
COPY --from=build /workspace/config-repo /config-repo
ENV SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS=file:/config-repo

USER app
EXPOSE 8888

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD curl -fsS http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
