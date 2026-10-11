# Pasos para desplegar el sistema en la nube (AWS)

Se describen dos rutas. La **Ruta A** (EC2 + Docker Compose) es la mas directa y reproduce exactamente el
entorno probado localmente. La **Ruta B** (ECS Fargate + servicios administrados) es la evolucion productiva.

## Servicios AWS utilizados

| Necesidad | Ruta A (simple) | Ruta B (productiva) |
|---|---|---|
| Computo | 1 instancia EC2 (t3.xlarge, 16 GB) | ECS Fargate (1 servicio por microservicio) |
| Imagenes | build en la instancia | Amazon ECR |
| MySQL | contenedor `mysql` | Amazon RDS MySQL 8 (4 esquemas) |
| Kafka | contenedor `kafka` | Amazon MSK |
| Secretos | `.env` | AWS Secrets Manager |
| Entrada | Security Group (puertos 8081-8083) | Application Load Balancer + ACM (HTTPS) |
| Logs | `docker compose logs` | CloudWatch Logs |

## Ruta A - EC2 + Docker Compose

1. **Crear la instancia**: Amazon Linux 2023 / Ubuntu 24.04, t3.xlarge, disco 40 GB gp3, par de claves propio.
2. **Security Group**: entrada 22 (solo tu IP), 8081/8082/8083 (BFF). No exponer 3307, 9092, 8888, 9000 ni 8761 a Internet
   (si se necesita ver Eureka, restringir a tu IP).
3. **Instalar Docker y Compose**:
   ```bash
   sudo dnf install -y docker git && sudo systemctl enable --now docker
   sudo usermod -aG docker ec2-user
   sudo mkdir -p /usr/local/lib/docker/cli-plugins
   sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
     -o /usr/local/lib/docker/cli-plugins/docker-compose && sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
   ```
   (cerrar y abrir la sesion SSH para aplicar el grupo `docker`).
4. **Obtener el codigo y configurar secretos**:
   ```bash
   git clone https://github.com/AntonioMunozDuoc/EFT_Backend_III.git
   cp .env.example .env && nano .env      # CAMBIAR todos los secretos (no usar los de desarrollo)
   ```
5. **Levantar**: `docker compose up -d --build` y esperar `docker compose ps` en `healthy`.
6. **Verificar** desde tu equipo: `curl http://<IP-PUBLICA>:8083/actuator/health`. Obtener un token requiere llegar al
   auth-server; para pruebas externas ejecutar `scripts/smoke-test.sh` dentro de la instancia.
7. **HTTPS**: poner un ALB con certificado ACM delante de los BFF (target group a 8081/8082/8083, HTTP interno)
   o terminar TLS con Nginx + Let's Encrypt en la instancia.
8. **Escalar**: `docker compose up -d --scale cuentas-service=3 --scale pagos-service=3` (el limite es la memoria de la instancia).

## Ruta B - ECS Fargate + RDS + MSK

1. **Red**: VPC con 2 subredes privadas (servicios, RDS, MSK) y 2 publicas (ALB). Security Groups: ALB -> BFF; BFF -> microservicios;
   microservicios -> RDS (3306) y MSK (9094).
2. **ECR**: crear un repositorio por modulo y publicar las imagenes (el contexto de build es la raiz del repo):
   ```bash
   aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <cuenta>.dkr.ecr.<region>.amazonaws.com
   for M in config-server discovery-server auth-server clientes-service cuentas-service pagos-service batch-service \
            bff-web bff-mobile bff-atm audit-service; do
     docker build -f $M/Dockerfile -t <cuenta>.dkr.ecr.<region>.amazonaws.com/bancoxyz/$M:1.0 .
     docker push <cuenta>.dkr.ecr.<region>.amazonaws.com/bancoxyz/$M:1.0
   done
   ```
3. **RDS MySQL 8**: crear la instancia (Multi-AZ en produccion) y ejecutar `docker/mysql-init/01-databases.sql`.
   Un usuario por servicio con permisos solo sobre su esquema.
4. **MSK**: cluster con 3 brokers; crear los topicos `banco.movimientos` y `banco.alertas` (3 particiones).
5. **Secrets Manager**: guardar `DB_PASSWORD` y los secretos OAuth2; referenciarlos en las *task definitions*.
6. **ECS**: un *service* por modulo (Fargate, 0.5 vCPU / 1 GB como minimo; 1 vCPU / 2 GB para pagos y cuentas).
   Variables de entorno iguales a las del `docker-compose.yaml`, cambiando `DB_URL` (endpoint RDS) y
   `KAFKA_BOOTSTRAP_SERVERS` (brokers MSK). Orden de arranque: config -> discovery -> auth -> microservicios -> BFF.
   *Eureka + LoadBalancer siguen funcionando dentro de la VPC* (con `prefer-ip-address=true`).
7. **Escalado horizontal**: Application Auto Scaling por servicio (objetivo CPU 60 %, minimo 2 tareas para `cuentas-service`
   y `pagos-service`). `batch-service` con 1 tarea; para ejecucion programada se puede lanzar el job con **EventBridge Scheduler**
   llamando a `POST /api/batch/jobs/{job}/ejecuciones`.
8. **ALB + HTTPS**: listener 443 con certificado ACM; reglas por ruta `/api/web/*`, `/api/mobile/*`, `/api/atm/*` hacia el target group de cada BFF.
9. **Observabilidad**: driver `awslogs` hacia CloudWatch; alarmas sobre `5xx` del ALB y CPU/memoria; `/actuator/health` como health check del target group.
10. **Limpieza** (evitar costos): eliminar servicios ECS, RDS, MSK, ALB y repositorios ECR no usados.

## Consideraciones de seguridad para la nube

- Reemplazar secretos `{noop}` del auth-server por BCrypt y externalizar las claves RSA (hoy se generan al arrancar).
- No exponer `auth-server`, `config-server` ni Eureka a Internet; los BFF son el unico borde.
- Cifrado en transito: HTTPS en el ALB y TLS en MSK/RDS; cifrado en reposo en RDS y volumenes EBS.
