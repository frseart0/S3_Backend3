package com.bancoxyz.bff.atm.dto;

/**
 * Sesion de cajero. Solo devuelve el primer nombre del titular para el saludo
 * en pantalla: un cajero es un dispositivo publico y no debe mostrar mas datos
 * personales de los necesarios.
 */
public record SesionAtm(
        String token,
        long expiraEnSegundos,
        String saludo,
        String dispositivoId) {
}
