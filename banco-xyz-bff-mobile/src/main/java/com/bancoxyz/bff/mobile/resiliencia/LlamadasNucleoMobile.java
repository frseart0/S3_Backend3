package com.bancoxyz.bff.mobile.resiliencia;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.core.CoreApiException;
import com.bancoxyz.bff.common.error.CanalNoDisponibleException;
import com.bancoxyz.domain.contract.ComprobanteOperacion;
import com.bancoxyz.domain.contract.PaginaMovimientos;
import com.bancoxyz.domain.contract.PerfilCliente;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.bancoxyz.domain.contract.SaldoCuenta;
import com.bancoxyz.domain.contract.SolicitudMovimiento;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Llamadas del canal movil. Si el servicio no responde, la app recibe un 503
 * en vez de un error generico. Los rechazos de negocio se propagan tal cual.
 */
@Service
public class LlamadasNucleoMobile {

    static final String INSTANCIA = "coreMobile";
    private static final String MENSAJE = "La aplicacion esta temporalmente no disponible";

    private final CoreApiClient core;

    public LlamadasNucleoMobile(CoreApiClient core) {
        this.core = core;
    }

    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "credencialesCaidas")
    @Retry(name = INSTANCIA)
    @TimeLimiter(name = INSTANCIA)
    public CompletableFuture<ResultadoAutenticacion> validarCredenciales(String usuario, String password) {
        return CompletableFuture.supplyAsync(() -> core.validarCredenciales(usuario, password));
    }

    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "saldoCaido")
    @Retry(name = INSTANCIA)
    @TimeLimiter(name = INSTANCIA)
    public CompletableFuture<SaldoCuenta> saldo(Long cuentaId) {
        return CompletableFuture.supplyAsync(() -> core.saldo(cuentaId));
    }

    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "movimientosCaidos")
    @Retry(name = INSTANCIA)
    @TimeLimiter(name = INSTANCIA)
    public CompletableFuture<PaginaMovimientos> movimientos(Long cuentaId, LocalDate desde, LocalDate hasta,
                                                           int pagina, int tamano) {
        return CompletableFuture.supplyAsync(() -> core.movimientos(cuentaId, desde, hasta, pagina, tamano));
    }

    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "clienteCaido")
    @Retry(name = INSTANCIA)
    @TimeLimiter(name = INSTANCIA)
    public CompletableFuture<PerfilCliente> perfilCliente(String usuario) {
        return CompletableFuture.supplyAsync(() -> core.perfilCliente(usuario));
    }

    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "depositoCaido")
    @Retry(name = INSTANCIA)
    @TimeLimiter(name = INSTANCIA)
    public CompletableFuture<ComprobanteOperacion> depositar(Long cuentaId, SolicitudMovimiento solicitud) {
        return CompletableFuture.supplyAsync(() -> core.depositar(cuentaId, solicitud));
    }

    private CompletableFuture<ResultadoAutenticacion> credencialesCaidas(String usuario, String password, Throwable causa) {
        return caido(causa);
    }

    private CompletableFuture<SaldoCuenta> saldoCaido(Long cuentaId, Throwable causa) {
        return caido(causa);
    }

    private CompletableFuture<PaginaMovimientos> movimientosCaidos(Long cuentaId, LocalDate desde, LocalDate hasta,
                                                                  int pagina, int tamano, Throwable causa) {
        return caido(causa);
    }

    private CompletableFuture<PerfilCliente> clienteCaido(String usuario, Throwable causa) {
        return caido(causa);
    }

    private CompletableFuture<ComprobanteOperacion> depositoCaido(Long cuentaId, SolicitudMovimiento solicitud,
                                                                  Throwable causa) {
        return caido(causa);
    }

    private static <T> CompletableFuture<T> caido(Throwable causa) {
        Throwable raiz = causa;
        while (raiz instanceof CompletionException && raiz.getCause() != null) {
            raiz = raiz.getCause();
        }
        if (raiz instanceof CoreApiException negocio) {
            return CompletableFuture.failedFuture(negocio);
        }
        if (raiz instanceof CanalNoDisponibleException canal) {
            return CompletableFuture.failedFuture(canal);
        }
        return CompletableFuture.failedFuture(
                new CanalNoDisponibleException("CANAL_NO_DISPONIBLE", MENSAJE, raiz));
    }
}
