package com.bancoxyz.core.reportes;

import com.bancoxyz.domain.contract.ResumenDiario;
import com.bancoxyz.domain.contract.TransaccionDiaria;
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
 * Reportes operativos de la migracion batch. Los consume el canal web, que es
 * el unico con espacio en pantalla para analizar anomalias; el movil y el
 * cajero no los exponen.
 */
@RestController
@RequestMapping("/internal")
@Tag(name = "Reportes", description = "Transacciones diarias y resumen del Job 1")
public class ReporteController {

    private static final int LIMITE_MAXIMO = 500;

    private final ReporteRepository reportes;

    public ReporteController(ReporteRepository reportes) {
        this.reportes = reportes;
    }

    @GetMapping("/transacciones")
    @Operation(summary = "Transacciones procesadas, filtrables por fecha y estado")
    public List<TransaccionDiaria> transacciones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "50") int limite) {

        if (limite <= 0 || limite > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("El limite debe estar entre 1 y " + LIMITE_MAXIMO);
        }
        return reportes.transacciones(fecha, estado, limite);
    }

    @GetMapping("/reportes/resumen-diario")
    @Operation(summary = "Resumen diario agregado por el Tasklet del Job 1")
    public List<ResumenDiario> resumenDiario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return reportes.resumenDiario(fecha);
    }
}
