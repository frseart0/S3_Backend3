package com.bancoxyz.bff.atm.resiliencia;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.core.CoreApiException;
import com.bancoxyz.bff.common.error.CanalNoDisponibleException;
import com.bancoxyz.domain.contract.ComprobanteRetiro;
import com.bancoxyz.domain.contract.PaginaMovimientos;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.bancoxyz.domain.contract.SaldoCuenta;
import com.bancoxyz.domain.contract.SolicitudPin;
import com.bancoxyz.domain.contract.SolicitudRetiro;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Llamadas del cajero al core-api con circuit breaker, reintento y limite de
 * tiempo. Si el nucleo no responde, el fallback no tumba el canal: informa
 * que el cajero esta temporalmente no disponible. Los rechazos de negocio
 * (saldo, PIN) se propagan tal cual.
 */
@Service
public class LlamadasNucleoAtm {

    static final String INSTANCIA = "coreAtm";
    private static final String MENSAJE = "El cajero esta temporalmente no disponible";

    private final CoreApiClient core;

    public LlamadasNucleoAtm(CoreApiClient core) {
        this.core = core;
    }

    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "pinCaido")
    @Retry(name = INSTANCIA)
    @TimeLimiter(name = INSTANCIA)
    public CompletableFuture<ResultadoAutenticacion> validarPin(SolicitudPin solicitud) {
        return CompletableFuture.supplyAsync(() -> core.validarPin(solicitud));
    }

    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "saldoCaido")
    @Retry(name = INSTANCIA)
    @TimeLimiter(name = INSTANCIA)
    public CompletableFuture<SaldoCuenta> saldo(Long cuentaId) {
        return CompletableFuture.supplyAsync(() -> core.saldo(cuentaId));
    }

    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "retiroCaido")
    @Retry(name = INSTANCIA)
    @TimeLimiter(name = INSTANCIA)
    public CompletableFuture<ComprobanteRetiro> retirar(Long cuentaId, SolicitudRetiro solicitud) {
        return CompletableFuture.supplyAsync(() -> core.retirar(cuentaId, solicitud));
    }

    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "movimientosCaidos")
    @Retry(name = INSTANCIA)
    @TimeLimiter(name = INSTANCIA)
    public CompletableFuture<PaginaMovimientos> movimientos(Long cuentaId,
                                                           LocalDate desde,
                                                           LocalDate hasta,
                                                           int pagina,
                                                           int tamano) {
        return CompletableFuture.supplyAsync(() -> core.movimientos(cuentaId, desde, hasta, pagina, tamano));
    }

    private CompletableFuture<ResultadoAutenticacion> pinCaido(SolicitudPin solicitud, Throwable causa) {
        return caido(causa);
    }

    private CompletableFuture<SaldoCuenta> saldoCaido(Long cuentaId, Throwable causa) {
        return caido(causa);
    }

    private CompletableFuture<ComprobanteRetiro> retiroCaido(Long cuentaId, SolicitudRetiro solicitud, Throwable causa) {
        return caido(causa);
    }

    private CompletableFuture<PaginaMovimientos> movimientosCaidos(Long cuentaId,
                                                                  LocalDate desde,
                                                                  LocalDate hasta,
                                                                  int pagina,
                                                                  int tamano,
                                                                  Throwable causa) {
        return caido(causa);
    }

    private static <T> CompletableFuture<T> caido(Throwable causa) {
        Throwable raiz = desenrollar(causa);
        if (raiz instanceof CoreApiException negocio) {
            return CompletableFuture.failedFuture(negocio);
        }
        if (raiz instanceof CanalNoDisponibleException canal) {
            return CompletableFuture.failedFuture(canal);
        }
        return CompletableFuture.failedFuture(new CanalNoDisponibleException(MENSAJE, raiz));
    }

    private static Throwable desenrollar(Throwable causa) {
        Throwable actual = causa;
        while (actual instanceof CompletionException && actual.getCause() != null) {
            actual = actual.getCause();
        }
        return actual;
    }
}
