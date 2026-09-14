package com.bancoxyz.batch.transacciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Transaccion diaria ya validada/clasificada, lista para persistir.
 * {@code estado} puede ser VALIDA, ANOMALIA o DUPLICADA -- el Job no
 * descarta las anomalias, las conserva para poder "detectar anomalias y
 * generar un resumen" como pide el requerimiento del negocio.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaccion {

    private Long idOrigen;
    private LocalDate fecha;
    private BigDecimal monto;
    private String tipo;
    private String estado;
    private String motivo;
    private String hilo;
}
