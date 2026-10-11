# Guion de video (5 a 7 minutos, MP4 con webcam + evidencias)

> Grabar con Kaltura (instructivo del AVA). Mantener la camara visible mientras se muestran las pantallas.
> **Antes de grabar**: ejecutar los 3 jobs, el smoke-test y tener Eureka abierto. Usar los resultados REALES de tu ejecucion.

| Tiempo | Seccion | Que decir / mostrar |
|---|---|---|
| 0:00-0:45 | **Resumen ejecutivo** | Banco XYZ con legacy COBOL/Shell. Objetivo: migrar a microservicios + batch + BFF. Mostrar diagrama de arquitectura (`docs/img/arquitectura.png`): 4 microservicios de negocio con BD propia, 3 BFF, plataforma Spring Cloud, Kafka. Aclarar que se corrigio la entrega anterior: el Core se dividio en clientes/cuentas/pagos |
| 0:45-2:15 | **Resultados** | (1) Eureka con 2 instancias de cuentas y pagos. (2) Ejecutar un job batch y mostrar `leidos/escritos/omitidos`, `/rechazos` y el CSV. (3) Transferencia por bff-web y por ATM un retiro. (4) Logs de `audit-service` con evento y alerta |
| 2:15-3:30 | **Comparacion con el legacy** | Tabla: escalabilidad (mainframe vertical vs replicas), despliegue (ventanas vs contenedores independientes), integracion (Shell/archivos vs REST+Kafka), seguridad (centralizada vs OAuth2 por scope), tolerancia a fallos (un fallo cae todo vs circuit breaker/saga), mantenimiento (COBOL vs Java/Spring), trazabilidad de rechazos |
| 3:30-5:30 | **Desafios y soluciones** | Datos sucios -> skip+auditoria. Consistencia distribuida sin 2PC -> saga con compensacion + idempotencia. Caida de BD/servicio -> retry con backoff, circuit breaker, restart. Kafka caido -> publicar AFTER_COMMIT con breaker. Autonomia de servicios -> BD por servicio. Mostrar `docker compose stop cuentas-service` y la respuesta 503 controlada |
| 5:30-6:30 | **Mejoras y proximos pasos** | Outbox pattern, API Gateway, tracing distribuido (OpenTelemetry), secretos en Secrets Manager + BCrypt, JWT con usuarios (authorization code), despliegue ECS/RDS/MSK, scheduler del batch, pruebas de contrato |
| 6:30-7:00 | Cierre | Resumen y entregables |
