-- ===========================================================================
-- Tablas de negocio del proyecto de migracion batch del Banco XYZ.
-- Las tablas de metadata de Spring Batch (BATCH_JOB_INSTANCE, etc.) se crean
-- automaticamente via spring.batch.jdbc.initialize-schema=always.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Job 1: Reporte de Transacciones Diarias
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transacciones (
    id                     BIGSERIAL PRIMARY KEY,
    transaccion_id_origen  BIGINT,
    fecha                  DATE,
    monto                  NUMERIC(15, 2),
    tipo                   VARCHAR(20),
    estado                 VARCHAR(20) NOT NULL,
    motivo                 VARCHAR(500),
    procesado_por_hilo     VARCHAR(60),
    procesado_en           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transacciones_fecha ON transacciones (fecha);
CREATE INDEX IF NOT EXISTS idx_transacciones_estado ON transacciones (estado);

CREATE TABLE IF NOT EXISTS resumen_transacciones_diarias (
    fecha                   DATE PRIMARY KEY,
    total_transacciones     INTEGER NOT NULL,
    total_validas           INTEGER NOT NULL,
    total_anomalias         INTEGER NOT NULL,
    total_duplicadas        INTEGER NOT NULL,
    monto_total_credito     NUMERIC(15, 2) NOT NULL,
    monto_total_debito      NUMERIC(15, 2) NOT NULL,
    generado_en             TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Job 2: Calculo de Intereses Mensuales
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cuentas_interes (
    id                  BIGSERIAL PRIMARY KEY,
    cuenta_id           BIGINT,
    nombre              VARCHAR(150),
    tipo                VARCHAR(20),
    saldo_inicial       NUMERIC(15, 2),
    edad_original       INTEGER,
    edad_utilizada       INTEGER,
    tasa_aplicada       NUMERIC(6, 4),
    interes_calculado   NUMERIC(15, 2),
    saldo_final         NUMERIC(15, 2),
    estado              VARCHAR(25) NOT NULL,
    observacion         VARCHAR(500),
    procesado_por_hilo  VARCHAR(60),
    procesado_en        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cuentas_interes_cuenta_id ON cuentas_interes (cuenta_id);
CREATE INDEX IF NOT EXISTS idx_cuentas_interes_tipo ON cuentas_interes (tipo);

-- ---------------------------------------------------------------------------
-- Job 3: Generacion de Estados de Cuenta Anuales
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS movimientos_anuales (
    id                  BIGSERIAL PRIMARY KEY,
    cuenta_id           BIGINT,
    fecha               DATE,
    tipo_transaccion    VARCHAR(20),
    monto               NUMERIC(15, 2),
    descripcion         VARCHAR(255),
    estado              VARCHAR(25) NOT NULL,
    motivo              VARCHAR(500),
    procesado_por_hilo  VARCHAR(60),
    procesado_en        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_movimientos_anuales_cuenta_id ON movimientos_anuales (cuenta_id);
CREATE INDEX IF NOT EXISTS idx_movimientos_anuales_fecha ON movimientos_anuales (fecha);

CREATE TABLE IF NOT EXISTS estado_cuenta_anual (
    cuenta_id             BIGINT NOT NULL,
    anio                  INTEGER NOT NULL,
    total_depositos       NUMERIC(15, 2) NOT NULL,
    total_salidas         NUMERIC(15, 2) NOT NULL,
    saldo_neto_anual      NUMERIC(15, 2) NOT NULL,
    cantidad_movimientos  INTEGER NOT NULL,
    cantidad_anomalias    INTEGER NOT NULL,
    generado_en           TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (cuenta_id, anio)
);

-- ---------------------------------------------------------------------------
-- Trazabilidad de errores / registros omitidos (tolerancia a fallos)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS batch_error_log (
    id              BIGSERIAL PRIMARY KEY,
    job_name        VARCHAR(100) NOT NULL,
    step_name       VARCHAR(100),
    etapa           VARCHAR(20) NOT NULL,
    detalle_item    VARCHAR(1000),
    mensaje_error   VARCHAR(1000),
    ocurrido_en     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_batch_error_log_job ON batch_error_log (job_name);
