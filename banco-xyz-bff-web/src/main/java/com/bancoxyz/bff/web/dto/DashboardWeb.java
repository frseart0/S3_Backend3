package com.bancoxyz.bff.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta compuesta del dashboard: perfil, saldo, cartola reciente,
 * intereses y estados anuales en una sola llamada. Es el aporte central del
 * BFF web: la pantalla principal se arma con un unico viaje al servidor en vez
 * de cinco desde el navegador.
 *
 * <p>{@code intereses} puede venir nulo si el dataset legacy no dejo un
 * calculo confiable para la cuenta; el resto del dashboard se entrega igual.
 */
public record DashboardWeb(
        PerfilWeb perfil,
        SaldoWeb saldo,
        List<MovimientoWeb> ultimosMovimientos,
        InteresWeb intereses,
        List<EstadoAnualWeb> estadosAnuales,
        ResumenCartolaWeb resumen,
        Instant generadoEn) {
}
