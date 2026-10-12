# Arquitectura de eventos de los retiros

El banco usa **notificacion de eventos con outbox transaccional**. No es event sourcing: el saldo sigue viviendo en `cuenta_saldo` y el core-api es la unica fuente de verdad. Kafka solo avisa que un retiro ya quedo confirmado, para que otro servicio lo procese sin acoplar el cajero al comprobante asincrono.

Se eligio Kafka y no JMS porque el despliegue en la nube ya orquesta un broker aparte (`docker-compose.yml`) y el contrato son topicos, no colas de un servidor de aplicaciones.

## Topicos

| Topico | Quien publica | Quien consume | Contenido |
| --- | --- | --- | --- |
| `banco.retiros.realizados` | `banco-xyz-pagos` (publicador del outbox) | `banco-xyz-notificaciones` | Retiro confirmado: `codigoAutorizacion`, `cuentaId`, `monto`, `canal`, `claveIdempotencia`, `saldoResultante`, `referenciaDispositivo` |
| `banco.transacciones.completadas` | `banco-xyz-pagos` | `banco-xyz-notificaciones` | Deposito, pago o transferencia confirmados: `tipo`, `codigoAutorizacion`, `cuentaId`, `cuentaDestinoId`, `monto`, `canal`, `claveIdempotencia`, `saldoResultante` |
| `banco.retiros.dlq` | el consumidor, cuando agota los reintentos | operacion / reproceso manual | El mensaje de retiro o de transaccion que no se pudo guardar |

La clave del mensaje es `claveIdempotencia`. El consumidor la usa como clave unica de `notificacion_retiro`, asi un reintento no duplica el comprobante.

## Flujo

```mermaid
flowchart LR
  atm[bff-atm]
  pagos[pagos]
  outbox[(outbox_evento)]
  saldo[(cuenta_saldo)]
  kafkaRetiro[banco.retiros.realizados]
  kafkaTx[banco.transacciones.completadas]
  dlq[banco.retiros.dlq]
  notas[notificaciones]
  bitacora[(notificacion_retiro)]
  bitacoraTx[(notificacion_transaccion)]

  atm -->|POST retiro| pagos
  pagos -->|misma transaccion| saldo
  pagos -->|misma transaccion| outbox
  outbox -->|retiro.realizado| kafkaRetiro
  outbox -->|transaccion.completada| kafkaTx
  kafkaRetiro --> notas
  kafkaTx --> notas
  notas --> bitacora
  notas --> bitacoraTx
  notas -->|fallo agotado| dlq
```

1. El cajero pide el retiro a `banco-xyz-pagos`. Un deposito, pago o transferencia sigue el mismo camino.
2. En una sola transaccion el servicio descuenta o acredita el saldo, escribe la bitacora y deja una fila en `outbox_evento`.
3. Un publicador lee las filas no publicadas. Los retiros van a `banco.retiros.realizados` y el resto a `banco.transacciones.completadas`. Si Kafka no esta, la fila queda pendiente y se reintenta: el cliente no se cobra dos veces.
4. `banco-xyz-notificaciones` persiste el comprobante. Si el mensaje es invalido y los reintentos se agotan, va a `banco.retiros.dlq`.
