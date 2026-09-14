package com.bancoxyz.core.cuentas;

import com.bancoxyz.core.config.RetiroProperties;
import com.bancoxyz.core.error.OperacionRechazadaException;
import com.bancoxyz.core.error.RecursoNoEncontradoException;
import com.bancoxyz.domain.contract.ComprobanteRetiro;
import com.bancoxyz.domain.contract.SolicitudRetiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de integridad del nucleo, verificadas sin base de datos. El
 * {@link TransactionTemplate} se reemplaza por uno que solo ejecuta el bloque,
 * porque lo que se prueba aqui son las decisiones, no el aislamiento de
 * PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class RetiroServiceTest {

    private static final long CUENTA = 106L;

    @Mock
    private RetiroRepository retiros;

    private RetiroService servicio;

    @BeforeEach
    void crearServicio() {
        var limites = new RetiroProperties(
                new BigDecimal("1000"), new BigDecimal("200000"), new BigDecimal("400000"));
        servicio = new RetiroService(retiros, limites, transaccionesSinBd());
    }

    @Test
    void descuentaElSaldoYRegistraLaOperacionYElMovimiento() {
        when(retiros.buscarPorClaveIdempotencia("clave-1")).thenReturn(Optional.empty());
        when(retiros.bloquearSaldo(CUENTA)).thenReturn(Optional.of(new BigDecimal("7035.00")));
        when(retiros.totalRetiradoHoy(CUENTA)).thenReturn(BigDecimal.ZERO);

        ComprobanteRetiro comprobante = servicio.retirar(CUENTA, solicitud("2000", "clave-1"));

        assertThat(comprobante.saldoResultante()).isEqualByComparingTo("5035.00");
        assertThat(comprobante.montoRetirado()).isEqualByComparingTo("2000");
        assertThat(comprobante.codigoAutorizacion()).hasSize(8);
        assertThat(comprobante.reintento()).isFalse();

        verify(retiros).actualizarSaldo(CUENTA, new BigDecimal("5035.00"));
        verify(retiros).registrarOperacion(eq("clave-1"), anyString(), eq(CUENTA), eq("atm"),
                eq(new BigDecimal("2000")), eq(new BigDecimal("5035.00")), eq("ATM-001"));
        verify(retiros).registrarMovimiento(eq(CUENTA), eq(new BigDecimal("2000")), eq("atm"), anyString());
    }

    /** La segunda vez que llega la misma orden se devuelve el comprobante original. */
    @Test
    void noVuelveAAplicarUnaOrdenYaProcesada() {
        var original = new ComprobanteRetiro("PN79Z47A", CUENTA, new BigDecimal("2000"),
                new BigDecimal("5035.00"), Instant.parse("2026-01-01T12:00:00Z"), true);
        when(retiros.buscarPorClaveIdempotencia("clave-repetida")).thenReturn(Optional.of(original));

        ComprobanteRetiro comprobante = servicio.retirar(CUENTA, solicitud("2000", "clave-repetida"));

        assertThat(comprobante.reintento()).isTrue();
        assertThat(comprobante.codigoAutorizacion()).isEqualTo("PN79Z47A");
        verify(retiros, never()).bloquearSaldo(anyLong());
        verify(retiros, never()).actualizarSaldo(anyLong(), any());
    }

    /**
     * Dos solicitudes identicas simultaneas: la que pierde la carrera choca con
     * la restriccion UNIQUE y debe devolver el comprobante de la ganadora.
     */
    @Test
    void resuelveLaCarreraDeDosSolicitudesIdenticas() {
        var ganadora = new ComprobanteRetiro("PN79Z47A", CUENTA, new BigDecimal("2000"),
                new BigDecimal("5035.00"), Instant.parse("2026-01-01T12:00:00Z"), true);
        when(retiros.buscarPorClaveIdempotencia("clave-carrera"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ganadora));
        when(retiros.bloquearSaldo(CUENTA)).thenReturn(Optional.of(new BigDecimal("7035.00")));
        when(retiros.totalRetiradoHoy(CUENTA)).thenReturn(BigDecimal.ZERO);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("clave_idempotencia duplicada"))
                .when(retiros).registrarOperacion(anyString(), anyString(), anyLong(), anyString(),
                        any(), any(), any());

        ComprobanteRetiro comprobante = servicio.retirar(CUENTA, solicitud("2000", "clave-carrera"));

        assertThat(comprobante.codigoAutorizacion()).isEqualTo("PN79Z47A");
        assertThat(comprobante.reintento()).isTrue();
    }

    @Test
    void rechazaUnRetiroSinSaldoSuficiente() {
        when(retiros.buscarPorClaveIdempotencia(anyString())).thenReturn(Optional.empty());
        when(retiros.bloquearSaldo(CUENTA)).thenReturn(Optional.of(new BigDecimal("1500.00")));
        when(retiros.totalRetiradoHoy(CUENTA)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> servicio.retirar(CUENTA, solicitud("2000", "clave-2")))
                .isInstanceOf(OperacionRechazadaException.class)
                .hasMessageContaining("Saldo insuficiente");

        verify(retiros, never()).actualizarSaldo(anyLong(), any());
    }

    @Test
    void rechazaUnRetiroQueExcedeElLimiteDiario() {
        when(retiros.buscarPorClaveIdempotencia(anyString())).thenReturn(Optional.empty());
        when(retiros.bloquearSaldo(CUENTA)).thenReturn(Optional.of(new BigDecimal("900000.00")));
        when(retiros.totalRetiradoHoy(CUENTA)).thenReturn(new BigDecimal("399000"));

        assertThatThrownBy(() -> servicio.retirar(CUENTA, solicitud("2000", "clave-3")))
                .isInstanceOf(OperacionRechazadaException.class)
                .hasMessageContaining("limite diario");
    }

    @Test
    void rechazaUnMontoBajoElMinimo() {
        assertThatThrownBy(() -> servicio.retirar(CUENTA, solicitud("500", "clave-4")))
                .isInstanceOf(OperacionRechazadaException.class)
                .hasMessageContaining("monto minimo");
    }

    @Test
    void rechazaUnMontoSobreElMaximoPorOperacion() {
        assertThatThrownBy(() -> servicio.retirar(CUENTA, solicitud("300000", "clave-5")))
                .isInstanceOf(OperacionRechazadaException.class)
                .hasMessageContaining("monto maximo");
    }

    @Test
    void exigeClaveDeIdempotencia() {
        assertThatThrownBy(() -> servicio.retirar(CUENTA,
                new SolicitudRetiro(new BigDecimal("2000"), "atm", "  ", "ATM-001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencia");
    }

    @Test
    void falla404SiLaCuentaNoTieneSaldoRegistrado() {
        when(retiros.buscarPorClaveIdempotencia(anyString())).thenReturn(Optional.empty());
        when(retiros.bloquearSaldo(CUENTA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.retirar(CUENTA, solicitud("2000", "clave-6")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private SolicitudRetiro solicitud(String monto, String clave) {
        return new SolicitudRetiro(new BigDecimal(monto), "atm", clave, "ATM-001");
    }

    private TransactionTemplate transaccionesSinBd() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> accion) {
                return accion.doInTransaction(new SimpleTransactionStatus());
            }
        };
    }
}
