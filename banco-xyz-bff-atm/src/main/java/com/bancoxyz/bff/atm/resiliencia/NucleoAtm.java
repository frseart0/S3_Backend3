package com.bancoxyz.bff.atm.resiliencia;

import com.bancoxyz.bff.common.error.CanalNoDisponibleException;
import com.bancoxyz.domain.contract.ComprobanteRetiro;
import com.bancoxyz.domain.contract.PaginaMovimientos;
import com.bancoxyz.domain.contract.ResultadoAutenticacion;
import com.bancoxyz.domain.contract.SaldoCuenta;
import com.bancoxyz.domain.contract.SolicitudPin;
import com.bancoxyz.domain.contract.SolicitudRetiro;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Fachada sincrona sobre {@link LlamadasNucleoAtm}. Las anotaciones de
 * Resilience4j viven en el otro bean: si este metodo se llamara a si mismo,
 * el aspect no las veria.
 */
@Service
public class NucleoAtm {

    private final LlamadasNucleoAtm llamadas;

    public NucleoAtm(LlamadasNucleoAtm llamadas) {
        this.llamadas = llamadas;
    }

    public ResultadoAutenticacion validarPin(SolicitudPin solicitud) {
        return esperar(llamadas.validarPin(solicitud));
    }

    public SaldoCuenta saldo(Long cuentaId) {
        return esperar(llamadas.saldo(cuentaId));
    }

    public ComprobanteRetiro retirar(Long cuentaId, SolicitudRetiro solicitud) {
        return esperar(llamadas.retirar(cuentaId, solicitud));
    }

    public PaginaMovimientos movimientos(Long cuentaId, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        return esperar(llamadas.movimientos(cuentaId, desde, hasta, pagina, tamano));
    }

    private static <T> T esperar(CompletableFuture<T> futuro) {
        try {
            return futuro.join();
        } catch (CompletionException ex) {
            Throwable causa = ex.getCause() == null ? ex : ex.getCause();
            if (causa instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new CanalNoDisponibleException("El cajero esta temporalmente no disponible", causa);
        }
    }
}
