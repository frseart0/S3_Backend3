package com.bancoxyz.batch.cuentasanuales;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fila cruda de {@code cuentas_anuales.csv} (historial de operaciones
 * anuales por cuenta), tal cual llega del sistema legacy.
 */
@Data
@NoArgsConstructor
public class MovimientoRaw {

    private String cuentaId;
    private String fecha;
    private String transaccion;
    private String monto;
    private String descripcion;
}
