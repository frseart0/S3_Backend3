package com.bancoxyz.batch.cuentasanuales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Movimiento anual ya validado/normalizado, listo para persistir y para ser
 * agregado en el estado de cuenta anual (Tasklet posterior).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoAnual {

    private Long cuentaId;
    private LocalDate fecha;
    private String tipoTransaccion;
    private BigDecimal monto;
    private String descripcion;
    private String estado;
    private String motivo;
    private String hilo;
}
