# Instrucciones para ejecutar y probar cada componente

## 0. Requisitos

- Docker Desktop / Docker Engine con **Compose v2** (`docker compose version`).
- 8 GB de RAM libres (son ~13 contenedores). Puertos libres: 3307, 8081-8083, 8087, 8761, 8888, 9000, 9092.
- Para ejecucion local sin Docker: JDK 21 y Maven (`./mvnw`).

## 1. Levantar toda la plataforma

```bash
cp .env.example .env              # opcional: si no existe se usan valores de desarrollo
docker compose up -d --build      # la primera vez compila las imagenes (varios minutos)
docker compose ps                 # esperar a que todos esten "healthy"
```

> Si ya existia un volumen de MySQL de la entrega anterior: `docker compose down -v` antes de levantar,
> para que se creen las 4 bases (una por microservicio).

Verificaciones rapidas:

| Que | URL / comando | Esperado |
|---|---|---|
| Eureka (ver instancias) | http://localhost:8761 | `CUENTAS-SERVICE` y `PAGOS-SERVICE` con **2 instancias**, `CLIENTES-SERVICE`, `BATCH-SERVICE`, 3 BFF |
| Config Server | http://localhost:8888/pagos-service/default | propiedades de `pagos-service` |
| JWKS | http://localhost:9000/oauth2/jwks | claves publicas RSA |

## 2. Obtener tokens (OAuth2 client_credentials)

```bash
TOKEN_WEB=$(curl -s -u web-app:web-secret-dev -d "grant_type=client_credentials&scope=web.access" \
  http://localhost:9000/oauth2/token | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
TOKEN_MOBILE=$(curl -s -u mobile-app:mobile-secret-dev -d "grant_type=client_credentials&scope=mobile.access" \
  http://localhost:9000/oauth2/token | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
TOKEN_ATM=$(curl -s -u atm-app:atm-secret-dev -d "grant_type=client_credentials&scope=atm.access" \
  http://localhost:9000/oauth2/token | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
TOKEN_BATCH=$(curl -s -u batch-operator:batch-operator-secret-dev -d "grant_type=client_credentials&scope=batch.access" \
  http://localhost:9000/oauth2/token | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
```

## 3. Probar los BFF (cada canal con su token)

```bash
# --- Web (datos completos) ---
curl -H "Authorization: Bearer $TOKEN_WEB" http://localhost:8081/api/web/cuentas
curl -H "Authorization: Bearer $TOKEN_WEB" http://localhost:8081/api/web/dashboard/101
curl -H "Authorization: Bearer $TOKEN_WEB" -H "Content-Type: application/json" \
  -d '{"cuentaOrigen":101,"cuentaDestino":106,"monto":50,"descripcion":"Prueba"}' \
  http://localhost:8081/api/web/transferencias

# --- Mobile (respuestas livianas) ---
curl -H "Authorization: Bearer $TOKEN_MOBILE" http://localhost:8082/api/mobile/cuentas/101

# --- ATM (saldo y retiro) ---
curl -H "Authorization: Bearer $TOKEN_ATM" http://localhost:8083/api/atm/saldo/101
curl -H "Authorization: Bearer $TOKEN_ATM" -H "Content-Type: application/json" \
  -d '{"cuentaId":101,"monto":100}' http://localhost:8083/api/atm/retiros
```

Pruebas de seguridad:

```bash
curl -i http://localhost:8081/api/web/cuentas                                   # 401 sin token
curl -i -H "Authorization: Bearer $TOKEN_ATM" http://localhost:8081/api/web/cuentas  # 403 scope equivocado
```

## 4. Probar cada microservicio

Los microservicios no publican puertos al host (se escalan a varias replicas). Se prueban **a traves del BFF**
(seccion 3) o entrando a la red de Docker:

```bash
# Token de servicio con acceso a los 3 microservicios (el de bff-web)
T=$(curl -s -u bff-web:bff-web-secret-dev -d "grant_type=client_credentials&scope=cuentas.access clientes.access pagos.access" \
  http://localhost:9000/oauth2/token | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

# clientes-service
docker compose exec bff-web curl -s -H "Authorization: Bearer $T" http://clientes-service:8085/api/clientes
# cuentas-service: apertura de cuenta para el cliente 2
docker compose exec bff-web curl -s -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"clienteId":2,"tipo":"ahorro","saldoInicial":1000}' http://cuentas-service:8084/api/cuentas
# pagos-service: pago, deposito y consulta
docker compose exec bff-web curl -s -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"cuentaId":101,"monto":25.50,"comercio":"Libreria"}' http://pagos-service:8086/api/pagos
docker compose exec bff-web curl -s -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"cuentaId":101,"monto":200}' http://pagos-service:8086/api/pagos/depositos
```

Reglas para verificar: cierre de cuenta solo con saldo 0 (`POST /api/cuentas/{id}/cierre` -> 409 si hay saldo),
saldo insuficiente -> 409, referencia repetida -> misma transaccion (idempotencia).

## 5. Probar los procesos batch (Spring Batch)

```bash
# Los 3 jobs sobre el dataset con errores intencionales (1000 filas cada uno)
for J in reporteTransaccionesDiariasJob calculoInteresesMensualesJob estadosCuentaAnualesJob; do
  curl -s -X POST -H "Authorization: Bearer $TOKEN_BATCH" \
    "http://localhost:8087/api/batch/jobs/$J/ejecuciones?dataset=semana_3"; echo
done

curl -s -H "Authorization: Bearer $TOKEN_BATCH" http://localhost:8087/api/batch/resultados
curl -s -H "Authorization: Bearer $TOKEN_BATCH" "http://localhost:8087/api/batch/rechazos?job=calculoInteresesMensualesJob&limite=10"

# Reportes CSV generados
docker compose cp batch-service:/reports ./reports-generados
```

**Equivalencia con el legacy:** `python3 scripts/verificar-equivalencia.py batch-service/src/main/resources/data semana_1 semana_3`
recalcula con las mismas reglas, de forma independiente, los totales esperados (leidos / escritos / omitidos /
sumas). Comparar contra la respuesta de cada ejecucion y contra `/api/batch/resultados`
(ejecutar primero `semana_1` y despues `semana_3` en BD limpia para comparar cada dataset por separado).

**Reejecucion / restart:** detener MySQL durante una ejecucion (`docker compose stop mysql`) y volver a iniciarlo:
los reintentos por backoff y la reanudacion automatica (`batch.reejecucion.max-intentos`) se ven en
`docker compose logs -f batch-service`.

## 6. Probar resiliencia

```bash
# a) Cuentas caido: el BFF responde 503 controlado (Circuit Breaker) y pagos deja la transaccion PENDIENTE
docker compose stop cuentas-service
curl -i -H "Authorization: Bearer $TOKEN_ATM" -H "Content-Type: application/json" \
  -d '{"cuentaId":101,"monto":10}' http://localhost:8083/api/atm/retiros       # 503
docker compose start cuentas-service

# b) Una replica de cuentas caida: el trafico sigue (balanceo entre instancias vivas)
docker stop $(docker ps -q -f name=cuentas-service | head -1)
curl -s -H "Authorization: Bearer $TOKEN_ATM" http://localhost:8083/api/atm/saldo/101

# c) Kafka caido: los pagos siguen funcionando; el circuito kafkaPublisher se abre
docker compose stop kafka
curl -s -H "Authorization: Bearer $TOKEN_ATM" -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":5}' http://localhost:8083/api/atm/retiros
docker compose start kafka

# d) Compensacion (saga): transferencia a cuenta inexistente devuelve el debito
curl -i -H "Authorization: Bearer $TOKEN_WEB" -H "Content-Type: application/json" \
  -d '{"cuentaOrigen":101,"cuentaDestino":9999,"monto":10}' http://localhost:8081/api/web/transferencias   # 409
```

## 7. Probar mensajeria (Kafka)

```bash
docker compose logs -f audit-service-1 audit-service-2
# Tras un retiro/transferencia se ven lineas "AUDITORIA [...] evento procesado".
# Una transferencia compensada o de monto >= 1.000.000 genera "ALERTAS [...]" (topico banco.alertas).
```

## 8. Escalado horizontal

```bash
docker compose up -d --scale cuentas-service=3 --scale pagos-service=3
# Eureka (http://localhost:8761) muestra 3 instancias de cada uno; las llamadas se reparten (round-robin).
```

## 9. Ejecucion local sin Docker (opcional)

Arrancar en este orden con `./mvnw -pl <modulo> spring-boot:run`: MySQL (puerto 3306 con las 4 bases),
Kafka, `config-server`, `discovery-server`, `auth-server`, `clientes-service`, `cuentas-service`, `pagos-service`,
`batch-service`, BFF. Variables utiles: `DB_PASSWORD`, `SQL_INIT_MODE=always`, `SSL_ENABLED=false`.

## 10. Detener

```bash
docker compose down        # conserva datos
docker compose down -v     # borra volumenes (BD y Kafka)
```
