package com.bancoxyz.bff.web.reportes;

import com.bancoxyz.bff.common.core.CoreApiClient;
import com.bancoxyz.bff.web.dto.ResumenDiarioWeb;
import com.bancoxyz.bff.web.dto.TransaccionDiariaWeb;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Reportes de la migracion batch. Solo existen en el canal web: son tablas
 * densas, pensadas para analisis en pantalla grande, y no aportan nada en una
 * app movil ni en un cajero.
 */
@RestController
@RequestMapping("/api/web/reportes")
@Tag(name = "Reportes web")
public class ReportesWebController {

    private final CoreApiClient core;

    public ReportesWebController(CoreApiClient core) {
        this.core = core;
    }

    @GetMapping("/transacciones-diarias")
    @Operation(summary = "Transacciones procesadas por el Job 1, filtrables por fecha y estado")
    public List<TransaccionDiariaWeb> transacciones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "50") int limite) {
        return core.transacciones(fecha, estado, limite).stream()
                .map(TransaccionDiariaWeb::desde)
                .toList();
    }

    @GetMapping("/resumen-diario")
    @Operation(summary = "Resumen diario agregado: validas, anomalias, duplicadas y montos por tipo")
    public List<ResumenDiarioWeb> resumenDiario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return core.resumenDiario(fecha).stream()
                .map(ResumenDiarioWeb::desde)
                .toList();
    }
}
