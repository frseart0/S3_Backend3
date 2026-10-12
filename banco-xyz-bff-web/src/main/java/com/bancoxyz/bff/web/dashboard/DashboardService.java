package com.bancoxyz.bff.web.dashboard;

import com.bancoxyz.bff.common.core.CoreApiException;
import com.bancoxyz.bff.common.resiliencia.Espera;
import com.bancoxyz.bff.web.config.WebProperties;
import com.bancoxyz.bff.web.resiliencia.LlamadasNucleoWeb;
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

    private final LlamadasNucleoWeb nucleo;
    private final WebProperties propiedades;

    public DashboardService(LlamadasNucleoWeb nucleo, WebProperties propiedades) {
        this.nucleo = nucleo;
        this.propiedades = propiedades;
    }

    public DashboardWeb componer(long cuentaId) {
        int tamano = propiedades.movimientosDashboard();

        var perfil = nucleo.perfil(cuentaId);
        var saldo = nucleo.saldo(cuentaId);
        var movimientos = nucleo.movimientos(cuentaId, null, null, 0, tamano);
        var intereses = nucleo.intereses(cuentaId);
        var estadosAnuales = nucleo.estadosAnuales(cuentaId);

        PaginaMovimientos pagina = Espera.join(movimientos);
        List<MovimientoWeb> ultimos = pagina.contenido().stream().map(MovimientoWeb::desde).toList();
        var detalleIntereses = opcional(intereses, "intereses");
        var estados = opcional(estadosAnuales, "estados anuales");

        return new DashboardWeb(
                PerfilWeb.desde(Espera.join(perfil)),
                SaldoWeb.desde(Espera.join(saldo)),
                ultimos,
                detalleIntereses == null ? null : InteresWeb.desde(detalleIntereses),
                estados == null ? List.of() : estados.stream().map(EstadoAnualWeb::desde).toList(),
                ResumenCartolaWeb.de(ultimos, pagina.totalElementos()),
                Instant.now());
    }

    /**
     * Una seccion que el servicio de cuentas no tiene (404) se omite; cualquier
     * otro error si se propaga.
     */
    private <T> T opcional(CompletableFuture<T> futuro, String seccion) {
        try {
            return Espera.join(futuro);
        } catch (CoreApiException ex) {
            if (ex.estado().value() == 404) {
                log.debug("El dashboard omite la seccion de {}: {}", seccion, ex.getMessage());
                return null;
            }
            throw ex;
        }
    }
}
