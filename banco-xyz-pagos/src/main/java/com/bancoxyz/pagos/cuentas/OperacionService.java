package com.bancoxyz.pagos.cuentas;

import com.bancoxyz.domain.contract.ComprobanteOperacion;
import com.bancoxyz.domain.contract.SolicitudMovimiento;
import com.bancoxyz.domain.contract.SolicitudTransferencia;
import com.bancoxyz.pagos.error.OperacionRechazadaException;
import com.bancoxyz.pagos.error.RecursoNoEncontradoException;
import com.bancoxyz.pagos.eventos.EventoTransaccionCompletada;
import com.bancoxyz.pagos.eventos.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * Depositos, pagos y transferencias. Comparten la bitacora y el outbox del
 * retiro: la clave de idempotencia es unica y el saldo se mueve en la misma
 * transaccion que el evento.
 */
@Service
public class OperacionService {

    private static final Logger log = LoggerFactory.getLogger(OperacionService.class);
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RetiroRepository operaciones;
    private final OutboxRepository outbox;
    private final TransactionTemplate transacciones;
    private final ObjectMapper objectMapper;
    private final SecureRandom aleatorio = new SecureRandom();

    public OperacionService(RetiroRepository operaciones,
                            OutboxRepository outbox,
                            TransactionTemplate transacciones,
                            ObjectMapper objectMapper) {
        this.operaciones = operaciones;
        this.outbox = outbox;
        this.transacciones = transacciones;
        this.objectMapper = objectMapper;
    }

    public ComprobanteOperacion depositar(long cuentaId, SolicitudMovimiento solicitud) {
        validarMonto(solicitud.monto(), solicitud.claveIdempotencia(), solicitud.canal());
        return aplicarIdempotente(solicitud.claveIdempotencia(), () -> transacciones.execute(estado -> {
            exigirAbierta(cuentaId);
            BigDecimal saldo = exigirSaldo(cuentaId);
            BigDecimal nuevo = saldo.add(solicitud.monto());
            String codigo = codigo();
            operaciones.actualizarSaldo(cuentaId, nuevo);
            operaciones.registrarOperacionTipo(solicitud.claveIdempotencia(), codigo, cuentaId, null,
                    "DEPOSITO", solicitud.canal(), solicitud.monto(), nuevo, solicitud.referencia());
            operaciones.registrarMovimientoFirmado(cuentaId, solicitud.monto(), "deposito",
                    "Deposito " + codigo);
            publicar("DEPOSITO", codigo, cuentaId, null, solicitud, nuevo);
            log.info("Deposito {} en cuenta {}: saldo {} -> {}", codigo, cuentaId, saldo, nuevo);
            return comprobante("DEPOSITO", codigo, cuentaId, null, solicitud.monto(), nuevo, false);
        }));
    }

    public ComprobanteOperacion pagar(long cuentaId, SolicitudMovimiento solicitud) {
        validarMonto(solicitud.monto(), solicitud.claveIdempotencia(), solicitud.canal());
        return aplicarIdempotente(solicitud.claveIdempotencia(), () -> transacciones.execute(estado -> {
            exigirAbierta(cuentaId);
            BigDecimal saldo = exigirSaldo(cuentaId);
            if (saldo.compareTo(solicitud.monto()) < 0) {
                throw new OperacionRechazadaException("SALDO_INSUFICIENTE",
                        "Saldo insuficiente: disponible " + saldo + ", solicitado " + solicitud.monto());
            }
            BigDecimal nuevo = saldo.subtract(solicitud.monto());
            String codigo = codigo();
            operaciones.actualizarSaldo(cuentaId, nuevo);
            operaciones.registrarOperacionTipo(solicitud.claveIdempotencia(), codigo, cuentaId, null,
                    "PAGO", solicitud.canal(), solicitud.monto(), nuevo, solicitud.referencia());
            operaciones.registrarMovimientoFirmado(cuentaId, solicitud.monto().negate(), "pago",
                    "Pago " + codigo);
            publicar("PAGO", codigo, cuentaId, null, solicitud, nuevo);
            log.info("Pago {} en cuenta {}: saldo {} -> {}", codigo, cuentaId, saldo, nuevo);
            return comprobante("PAGO", codigo, cuentaId, null, solicitud.monto(), nuevo, false);
        }));
    }

    public ComprobanteOperacion transferir(long cuentaId, SolicitudTransferencia solicitud) {
        validarMonto(solicitud.monto(), solicitud.claveIdempotencia(), solicitud.canal());
        if (solicitud.cuentaDestinoId() == null) {
            throw new IllegalArgumentException("La cuenta de destino es obligatoria");
        }
        if (solicitud.cuentaDestinoId() == cuentaId) {
            throw new OperacionRechazadaException("CUENTA_INVALIDA",
                    "La cuenta de origen y la de destino no pueden ser la misma");
        }
        return aplicarIdempotente(solicitud.claveIdempotencia(), () -> transacciones.execute(estado -> {
            long destino = solicitud.cuentaDestinoId();
            long menor = Math.min(cuentaId, destino);
            long mayor = Math.max(cuentaId, destino);
            exigirAbierta(menor);
            exigirAbierta(mayor);
            BigDecimal saldoMenor = exigirSaldo(menor);
            BigDecimal saldoMayor = exigirSaldo(mayor);
            BigDecimal saldoOrigen = cuentaId == menor ? saldoMenor : saldoMayor;
            if (saldoOrigen.compareTo(solicitud.monto()) < 0) {
                throw new OperacionRechazadaException("SALDO_INSUFICIENTE",
                        "Saldo insuficiente: disponible " + saldoOrigen + ", solicitado " + solicitud.monto());
            }
            BigDecimal nuevoOrigen = saldoOrigen.subtract(solicitud.monto());
            BigDecimal saldoDestino = cuentaId == menor ? saldoMayor : saldoMenor;
            BigDecimal nuevoDestino = saldoDestino.add(solicitud.monto());
            String codigo = codigo();
            operaciones.actualizarSaldo(cuentaId, nuevoOrigen);
            operaciones.actualizarSaldo(destino, nuevoDestino);
            operaciones.registrarOperacionTipo(solicitud.claveIdempotencia(), codigo, cuentaId, destino,
                    "TRANSFERENCIA", solicitud.canal(), solicitud.monto(), nuevoOrigen, null);
            operaciones.registrarMovimientoFirmado(cuentaId, solicitud.monto().negate(), "transferencia",
                    "Transferencia enviada " + codigo);
            operaciones.registrarMovimientoFirmado(destino, solicitud.monto(), "transferencia",
                    "Transferencia recibida " + codigo);
            publicarTransferencia(codigo, cuentaId, destino, solicitud, nuevoOrigen);
            log.info("Transferencia {} de {} a {} por {}", codigo, cuentaId, destino, solicitud.monto());
            return comprobante("TRANSFERENCIA", codigo, cuentaId, destino, solicitud.monto(), nuevoOrigen, false);
        }));
    }

    private ComprobanteOperacion aplicarIdempotente(String clave, java.util.function.Supplier<ComprobanteOperacion> aplicar) {
        Optional<ComprobanteOperacion> previa = operaciones.buscarOperacion(clave);
        if (previa.isPresent()) {
            return previa.get();
        }
        try {
            return aplicar.get();
        } catch (DuplicateKeyException ex) {
            return operaciones.buscarOperacion(clave)
                    .orElseThrow(() -> new OperacionRechazadaException(
                            "OPERACION_DUPLICADA", "La operacion no se pudo confirmar, reintente la consulta"));
        }
    }

    private void exigirAbierta(long cuentaId) {
        if (operaciones.estaCerrada(cuentaId)) {
            throw new OperacionRechazadaException("CUENTA_CERRADA", "La cuenta " + cuentaId + " esta cerrada");
        }
    }

    private BigDecimal exigirSaldo(long cuentaId) {
        return operaciones.bloquearSaldo(cuentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cuenta " + cuentaId + " no tiene saldo registrado"));
    }

    private void publicar(String tipo, String codigo, long cuentaId, Long destino,
                          SolicitudMovimiento solicitud, BigDecimal saldo) {
        publicarEvento(new EventoTransaccionCompletada(
                tipo, codigo, cuentaId, destino, solicitud.monto(), solicitud.canal(),
                solicitud.claveIdempotencia(), saldo));
    }

    private void publicarTransferencia(String codigo, long origen, long destino,
                                       SolicitudTransferencia solicitud, BigDecimal saldo) {
        publicarEvento(new EventoTransaccionCompletada(
                "TRANSFERENCIA", codigo, origen, destino, solicitud.monto(), solicitud.canal(),
                solicitud.claveIdempotencia(), saldo));
    }

    private void publicarEvento(EventoTransaccionCompletada evento) {
        try {
            outbox.insertar("transaccion.completada", evento.claveIdempotencia(),
                    objectMapper.writeValueAsString(evento));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el evento de la transaccion", ex);
        }
    }

    private void validarMonto(BigDecimal monto, String clave, String canal) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia es obligatoria");
        }
        if (canal == null || canal.isBlank()) {
            throw new IllegalArgumentException("El canal de origen es obligatorio");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser positivo");
        }
    }

    private ComprobanteOperacion comprobante(String tipo, String codigo, long cuentaId, Long destino,
                                             BigDecimal monto, BigDecimal saldo, boolean reintento) {
        return new ComprobanteOperacion(tipo, codigo, cuentaId, destino, monto, saldo,
                java.time.Instant.now(), reintento);
    }

    private String codigo() {
        var codigo = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            codigo.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }
}
