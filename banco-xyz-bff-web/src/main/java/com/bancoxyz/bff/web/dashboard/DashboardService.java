package com.bancoxyz.bff.web.dashboard;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.common.core.CoreApiException;
import com.bancoxyz.bff.web.config.WebProperties;
import com.bancoxyz.bff.web.dto.DashboardWeb;
import com.bancoxyz.bff.web.dto.EstadoAnualWeb;
import com.bancoxyz.bff.web.dto.InteresWeb;
import com.bancoxyz.bff.web.dto.MovimientoWeb;
import com.bancoxyz.bff.web.dto.PerfilWeb;
import com.bancoxyz.bff.web.dto.ResumenCartolaWeb;
import com.bancoxyz.bff.web.dto.SaldoWeb;
import com.bancoxyz.domain.contract.PaginaMovimientos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Compone el dashboard de la banca en linea. Las cinco consultas al core-api
 * son independientes, asi que se lanzan en paralelo y la pantalla tarda lo que
 * la mas lenta.
 *
 * <p>Perfil y saldo son obligatorios: si fallan, la respuesta falla. El resto
 * son secciones opcionales, porque el dataset legacy no siempre dejo intereses
 * o estados anuales para todas las cuentas y no tiene sentido negarle el
 * dashboard completo a un cliente por eso.
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final CoreApiClient core;
    private final ExecutorService ejecutor;
    private final WebProperties propiedades;

    public DashboardService(CoreApiClient core, ExecutorService ejecutor, WebProperties propiedades) {
        this.core = core;
        this.ejecutor = ejecutor;
        this.propiedades = propiedades;
    }

    public DashboardWeb componer(long cuentaId) {
        int tamano = propiedades.movimientosDashboard();

        var perfil = tarea(() -> core.perfil(cuentaId));
        var saldo = tarea(() -> core.saldo(cuentaId));
        var movimientos = tarea(() -> core.movimientos(cuentaId, null, null, 0, tamano));
        var intereses = tareaOpcional(() -> core.intereses(cuentaId), "intereses");
        var estadosAnuales = tareaOpcional(() -> core.estadosAnuales(cuentaId), "estados anuales");

        // Las cinco tareas ya estan corriendo; aqui solo se recogen resultados.
        PaginaMovimientos pagina = esperar(movimientos);
        List<MovimientoWeb> ultimos = pagina.contenido().stream().map(MovimientoWeb::desde).toList();
        var detalleIntereses = esperar(intereses);
        var estados = esperar(estadosAnuales);

        return new DashboardWeb(
                PerfilWeb.desde(esperar(perfil)),
                SaldoWeb.desde(esperar(saldo)),
                ultimos,
                detalleIntereses == null ? null : InteresWeb.desde(detalleIntereses),
                estados == null ? List.of() : estados.stream().map(EstadoAnualWeb::desde).toList(),
                ResumenCartolaWeb.de(ultimos, pagina.totalElementos()),
                Instant.now());
    }

    private <T> CompletableFuture<T> tarea(Supplier<T> consulta) {
        return CompletableFuture.supplyAsync(consulta, ejecutor);
    }

    /**
     * Recoge el resultado desenvolviendo la {@link CompletionException} con la
     * que {@code join} envuelve los fallos: si no se desenvuelve, el manejador
     * de errores no reconoceria un {@link CoreApiException} y todo terminaria
     * como 500.
     */
    private <T> T esperar(CompletableFuture<T> futuro) {
        try {
            return futuro.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException causa) {
                throw causa;
            }
            throw ex;
        }
    }

    /**
     * Una seccion que el core no tiene (404) se omite; cualquier otro error si
     * se propaga, porque significa que el nucleo esta en problemas.
     */
    private <T> CompletableFuture<T> tareaOpcional(Supplier<T> consulta, String seccion) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return consulta.get();
            } catch (CoreApiException ex) {
                if (ex.estado().value() == 404) {
                    log.debug("El dashboard omite la seccion de {}: {}", seccion, ex.getMessage());
                    return null;
                }
                throw ex;
            }
        }, ejecutor);
    }
}
