package com.bancoxyz.bff.atm.autenticacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Datos que el cajero lee de la tarjeta y del teclado. El PIN se valida como
 * 4 digitos antes de salir del BFF: un formato invalido no debe gastar uno de
 * los intentos que el nucleo contabiliza.
 */
public record SolicitudSesionAtm(
        @NotBlank(message = "la tarjeta es obligatoria") String tarjeta,
        @NotBlank(message = "el PIN es obligatorio")
        @Pattern(regexp = "\\d{4}", message = "el PIN debe tener 4 digitos") String pin) {
}
