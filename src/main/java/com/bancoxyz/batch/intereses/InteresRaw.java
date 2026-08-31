package com.bancoxyz.batch.intereses;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fila cruda de {@code intereses.csv} (cuentas bancarias y sus datos para el
 * calculo mensual de interes), tal cual llega del sistema legacy.
 */
@Data
@NoArgsConstructor
public class InteresRaw {

    private String cuentaId;
    private String nombre;
    private String saldo;
    private String edad;
    private String tipo;
}
