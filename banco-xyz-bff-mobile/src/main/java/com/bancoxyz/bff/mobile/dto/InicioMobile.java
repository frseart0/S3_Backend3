package com.bancoxyz.bff.mobile.dto;

import java.util.List;

/**
 * Pantalla de inicio de la app: saldo y los ultimos movimientos en una sola
 * llamada. Es el equivalente movil del dashboard web, pero con lo minimo para
 * pintar la primera pantalla; el resto se pide solo si el usuario navega.
 */
public record InicioMobile(String nombre, SaldoMobile saldo, List<MovimientoMobile> movimientos) {
}
