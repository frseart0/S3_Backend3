package com.bancoxyz.batch.intereses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Resultado del calculo de interes mensual de una cuenta, listo para
 * persistir. {@code estado=VALIDA_CORREGIDA} indica que se aplico alguna
 * correccion automatica (ej. edad fuera de rango) documentada en
 * {@code observacion}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CuentaInteres {

    private Long cuentaId;
    private String nombre;
    private String tipo;
    private BigDecimal saldoInicial;
    private Integer edadOriginal;
    private Integer edadUtilizada;
    private BigDecimal tasaAplicada;
    private BigDecimal interesCalculado;
    private BigDecimal saldoFinal;
    private String estado;
    private String observacion;
    private String hilo;
}
