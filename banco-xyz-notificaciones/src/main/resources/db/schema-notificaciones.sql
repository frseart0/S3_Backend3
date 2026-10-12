CREATE TABLE IF NOT EXISTS notificacion_transaccion (
    id                   BIGSERIAL PRIMARY KEY,
    clave_evento         VARCHAR(80) NOT NULL UNIQUE,
    tipo                 VARCHAR(30),
    codigo_autorizacion  VARCHAR(20),
    cuenta_id            BIGINT,
    cuenta_destino_id    BIGINT,
    monto                NUMERIC(15, 2),
    canal                VARCHAR(10),
    payload              TEXT NOT NULL,
    recibido_en          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS notificacion_retiro (
    id                   BIGSERIAL PRIMARY KEY,
    clave_evento         VARCHAR(80) NOT NULL UNIQUE,
    codigo_autorizacion  VARCHAR(20),
    cuenta_id            BIGINT,
    monto                NUMERIC(15, 2),
    canal                VARCHAR(10),
    payload              TEXT NOT NULL,
    recibido_en          TIMESTAMP NOT NULL DEFAULT now()
);
