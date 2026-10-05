package com.bancoxyz.core.cuentas;

import com.bancoxyz.core.config.RetiroProperties;
import com.bancoxyz.core.error.OperacionRechazadaException;
import com.bancoxyz.core.error.RecursoNoEncontradoException;
import com.bancoxyz.core.eventos.EventoRetiroRealizado;
import com.bancoxyz.core.eventos.OutboxRepository;
import com.bancoxyz.domain.contract.ComprobanteRetiro;
import com.bancoxyz.domain.contract.SolicitudRetiro;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

/**
 * Aplica retiros garantizando integridad y consistencia:
 *
 * <ol>
 *   <li><b>Idempotencia</b>: la clave de la solicitud es UNIQUE en la bitacora,
 *       asi que un reintento del cajero devuelve el comprobante original en vez
 *       de cobrar dos veces.</li>
 *   <li><b>Aislamiento</b>: la fila del saldo se bloquea con
 *       {@code SELECT ... FOR UPDATE}, de modo que dos canales operando sobre la
 *       misma cuenta se serializan en vez de leer un saldo obsoleto.</li>
 *   <li><b>Atomicidad</b>: descontar el saldo, registrar la operacion, dejar el
 *       movimiento y encolar el evento de Kafka ocurren en la misma transaccion.</li>
 * </ol>
 *
 * <p>La transaccion se maneja con {@link TransactionTemplate} y no con
 * {@code @Transactional} porque la deteccion del reintento necesita consultar
 * la base <em>despues</em> de que la insercion duplicada aborte la transaccion.
 */
@Service
public class RetiroService {

    private static final Logger log = LoggerFactory.getLogger(RetiroService.class);
    private static final String ALFABETO_AUTORIZACION = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LARGO_AUTORIZACION = 8;

    private final RetiroRepository retiros;
    private final OutboxRepository outbox;
    private final RetiroProperties limites;
    private final TransactionTemplate transacciones;
    private final ObjectMapper objectMapper;
    private final SecureRandom aleatorio = new SecureRandom();

    public RetiroService(RetiroRepository retiros,
                         OutboxRepository outbox,
                         RetiroProperties limites,
                         TransactionTemplate transacciones,
                         ObjectMapper objectMapper) {
        this.retiros = retiros;
        this.outbox = outbox;
        this.limites = limites;
        this.transacciones = transacciones;
        this.objectMapper = objectMapper;
    }

    public ComprobanteRetiro retirar(long cuentaId, SolicitudRetiro solicitud) {
        validarSolicitud(solicitud);

        Optional<ComprobanteRetiro> previo = retiros.buscarPorClaveIdempotencia(solicitud.claveIdempotencia());
        if (previo.isPresent()) {
            log.info("Retiro {} ya estaba aplicado: se devuelve el comprobante original",
                    solicitud.claveIdempotencia());
            return previo.get();
        }

        try {
            return aplicar(cuentaId, solicitud);
        } catch (DuplicateKeyException ex) {
            // Dos solicitudes identicas llegaron a la vez: una gano la carrera
            // y la otra debe devolver el mismo comprobante, no un error.
            return retiros.buscarPorClaveIdempotencia(solicitud.claveIdempotencia())
                    .orElseThrow(() -> new OperacionRechazadaException(
                            "RETIRO_DUPLICADO", "El retiro no se pudo confirmar, reintente la consulta"));
        }
    }

    private ComprobanteRetiro aplicar(long cuentaId, SolicitudRetiro solicitud) {
        return transacciones.execute(estado -> {
            BigDecimal saldo = retiros.bloquearSaldo(cuentaId)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "La cuenta " + cuentaId + " no tiene saldo registrado"));

            BigDecimal monto = solicitud.monto();
            BigDecimal retiradoHoy = retiros.totalRetiradoHoy(cuentaId);
            if (retiradoHoy.add(monto).compareTo(limites.limiteDiario()) > 0) {
                throw new OperacionRechazadaException("LIMITE_DIARIO_EXCEDIDO",
                        "El retiro supera el limite diario de " + limites.limiteDiario()
                                + " (hoy ya se retiraron " + retiradoHoy + ")");
            }
            if (saldo.compareTo(monto) < 0) {
                throw new OperacionRechazadaException("SALDO_INSUFICIENTE",
                        "Saldo insuficiente: disponible " + saldo + ", solicitado " + monto);
            }

            BigDecimal nuevoSaldo = saldo.subtract(monto);
            String codigo = generarCodigoAutorizacion();

            retiros.actualizarSaldo(cuentaId, nuevoSaldo);
            retiros.registrarOperacion(solicitud.claveIdempotencia(), codigo, cuentaId,
                    solicitud.canal(), monto, nuevoSaldo, solicitud.referenciaDispositivo());
            retiros.registrarMovimiento(cuentaId, monto, solicitud.canal(), codigo);
            publicarEvento(new EventoRetiroRealizado(
                    codigo, cuentaId, monto, solicitud.canal(), solicitud.claveIdempotencia(),
                    nuevoSaldo, solicitud.referenciaDispositivo()));

            log.info("Retiro {} aplicado en cuenta {} por canal {}: saldo {} -> {}",
                    codigo, cuentaId, solicitud.canal(), saldo, nuevoSaldo);

            return new ComprobanteRetiro(codigo, cuentaId, monto, nuevoSaldo, Instant.now(), false);
        });
    }

    private void publicarEvento(EventoRetiroRealizado evento) {
        try {
            outbox.insertar("retiro.realizado", evento.claveIdempotencia(), objectMapper.writeValueAsString(evento));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el evento de retiro", ex);
        }
    }

    private void validarSolicitud(SolicitudRetiro solicitud) {
        if (solicitud.claveIdempotencia() == null || solicitud.claveIdempotencia().isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia es obligatoria");
        }
        if (solicitud.canal() == null || solicitud.canal().isBlank()) {
            throw new IllegalArgumentException("El canal de origen es obligatorio");
        }
        BigDecimal monto = solicitud.monto();
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto del retiro debe ser positivo");
        }
        if (monto.compareTo(limites.montoMinimo()) < 0) {
            throw new OperacionRechazadaException("MONTO_FUERA_DE_RANGO",
                    "El monto minimo de retiro es " + limites.montoMinimo());
        }
        if (monto.compareTo(limites.montoMaximoOperacion()) > 0) {
            throw new OperacionRechazadaException("MONTO_FUERA_DE_RANGO",
                    "El monto maximo por operacion es " + limites.montoMaximoOperacion());
        }
    }

    private String generarCodigoAutorizacion() {
        var codigo = new StringBuilder(LARGO_AUTORIZACION);
        for (int i = 0; i < LARGO_AUTORIZACION; i++) {
            codigo.append(ALFABETO_AUTORIZACION.charAt(aleatorio.nextInt(ALFABETO_AUTORIZACION.length())));
        }
        return codigo.toString();
    }
}
