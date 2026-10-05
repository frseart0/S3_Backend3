-- ===========================================================================
-- Tablas que el core-api necesita y que los Jobs batch no producen:
-- identidades por canal, dispositivos de cajero, saldo vigente y bitacora de
-- operaciones. Se carga despues de db/schema-negocio.sql (modulo domain), del
-- que depende la siembra de saldos del final del archivo.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Identidades. Un mismo cliente puede estar habilitado en unos canales y no
-- en otros: 'canales_permitidos' es la lista separada por comas de los codigos
-- de canal (web, mobile, atm). La tarjeta y el PIN solo existen si el cliente
-- opera en cajeros.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuarios_canal (
    id                  BIGSERIAL PRIMARY KEY,
    usuario             VARCHAR(60) NOT NULL UNIQUE,
    password_hash       VARCHAR(120) NOT NULL,
    nombre              VARCHAR(150) NOT NULL,
    cuenta_id           BIGINT NOT NULL,
    canales_permitidos  VARCHAR(60) NOT NULL,
    tarjeta             VARCHAR(25) UNIQUE,
    pin_hash            VARCHAR(120),
    intentos_fallidos   INTEGER NOT NULL DEFAULT 0,
    bloqueado           BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_usuarios_canal_cuenta ON usuarios_canal (cuenta_id);

-- ---------------------------------------------------------------------------
-- Cajeros registrados. Un cajero es un cliente maquina: se autentica con su
-- propia clave antes de que el core acepte validar el PIN de una tarjeta.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dispositivos_atm (
    dispositivo_id  VARCHAR(40) PRIMARY KEY,
    clave_hash      VARCHAR(120) NOT NULL,
    ubicacion       VARCHAR(150),
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Saldo vigente por cuenta. Es la unica fila que se bloquea (SELECT ... FOR
-- UPDATE) durante un retiro, de modo que dos canales que operan sobre la
-- misma cuenta al mismo tiempo no puedan sobregirarla.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cuenta_saldo (
    cuenta_id       BIGINT PRIMARY KEY,
    saldo           NUMERIC(15, 2) NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    actualizado_en  TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Bitacora de operaciones con dinero. 'clave_idempotencia' es UNIQUE: si un
-- cajero reintenta la misma orden tras un timeout, la segunda insercion choca
-- y el core devuelve el comprobante original en vez de descontar dos veces.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS operaciones_cuenta (
    id                      BIGSERIAL PRIMARY KEY,
    clave_idempotencia      VARCHAR(80) NOT NULL UNIQUE,
    codigo_autorizacion     VARCHAR(20) NOT NULL,
    cuenta_id               BIGINT NOT NULL,
    tipo                    VARCHAR(20) NOT NULL,
    canal                   VARCHAR(10) NOT NULL,
    monto                   NUMERIC(15, 2) NOT NULL,
    saldo_resultante        NUMERIC(15, 2) NOT NULL,
    referencia_dispositivo  VARCHAR(40),
    realizado_en            TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_operaciones_cuenta_fecha
    ON operaciones_cuenta (cuenta_id, realizado_en);

-- ---------------------------------------------------------------------------
-- Outbox transaccional. El retiro y el evento se confirman juntos; un
-- publicador posterior los entrega a Kafka. Si el broker esta caido, la fila
-- queda pendiente y se reintenta sin volver a cobrar.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_evento (
    id              BIGSERIAL PRIMARY KEY,
    tipo            VARCHAR(80) NOT NULL,
    clave_evento    VARCHAR(80) NOT NULL UNIQUE,
    payload         TEXT NOT NULL,
    publicado       BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMP NOT NULL DEFAULT now(),
    publicado_en    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_evento_pendiente
    ON outbox_evento (publicado, id);

-- ---------------------------------------------------------------------------
-- Siembra del saldo vigente con el resultado del Job de intereses. El dataset
-- legacy trae varias filas por cuenta, por lo que se toma la ultima fila
-- confiable de cada una (DISTINCT ON) como saldo de partida. Si el Job todavia
-- no se ejecuto no inserta nada, y las cuentas ya sembradas no se tocan para
-- no perder los retiros ya aplicados.
-- ---------------------------------------------------------------------------
INSERT INTO cuenta_saldo (cuenta_id, saldo)
SELECT DISTINCT ON (cuenta_id) cuenta_id, saldo_final
FROM cuentas_interes
WHERE cuenta_id IS NOT NULL
  AND saldo_final IS NOT NULL
  AND estado IN ('VALIDA', 'VALIDA_CORREGIDA')
ORDER BY cuenta_id, procesado_en DESC, id DESC
ON CONFLICT (cuenta_id) DO NOTHING;
