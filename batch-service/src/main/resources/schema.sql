-- Tablas de resultado de los procesos batch (las tablas de metadatos de Spring Batch
-- las crea Spring Boot con spring.batch.jdbc.initialize-schema=always).

CREATE TABLE IF NOT EXISTS movimientos_diarios (
    id        BIGINT PRIMARY KEY,
    fecha     DATE           NOT NULL,
    monto     DECIMAL(15,2)  NOT NULL,
    tipo      VARCHAR(20)    NOT NULL,
    anomalia  VARCHAR(40)    NULL,
    dataset   VARCHAR(20)    NOT NULL
);

CREATE TABLE IF NOT EXISTS intereses_calculados (
    cuenta_id        BIGINT PRIMARY KEY,
    nombre           VARCHAR(100)   NOT NULL,
    tipo             VARCHAR(20)    NOT NULL,
    saldo            DECIMAL(15,2)  NOT NULL,
    edad             INT            NULL,
    tasa_anual       DECIMAL(8,4)   NOT NULL,
    interes_mensual  DECIMAL(15,2)  NOT NULL,
    periodo          VARCHAR(7)     NOT NULL,
    dataset          VARCHAR(20)    NOT NULL
);

CREATE TABLE IF NOT EXISTS estado_cuenta_movimientos (
    clave        CHAR(32) PRIMARY KEY,
    cuenta_id    BIGINT         NOT NULL,
    fecha        DATE           NOT NULL,
    transaccion  VARCHAR(20)    NOT NULL,
    monto        DECIMAL(15,2)  NOT NULL,
    descripcion  VARCHAR(255)   NOT NULL
);

CREATE TABLE IF NOT EXISTS estados_cuenta_anuales (
    cuenta_id             BIGINT         NOT NULL,
    anio                  INT            NOT NULL,
    total_depositos       DECIMAL(15,2)  NOT NULL,
    total_egresos         DECIMAL(15,2)  NOT NULL,
    saldo_neto            DECIMAL(15,2)  NOT NULL,
    cantidad_movimientos  INT            NOT NULL,
    PRIMARY KEY (cuenta_id, anio)
);

CREATE TABLE IF NOT EXISTS registros_rechazados (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    job            VARCHAR(60)   NOT NULL,
    dataset        VARCHAR(20)   NOT NULL,
    registro       VARCHAR(500)  NULL,
    motivo         VARCHAR(255)  NOT NULL,
    fecha_proceso  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
