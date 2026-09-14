package com.bancoxyz.bff.web.autenticacion;

import jakarta.validation.constraints.NotBlank;

public record CredencialesWeb(
        @NotBlank(message = "el usuario es obligatorio") String usuario,
        @NotBlank(message = "la contrasena es obligatoria") String password) {
}
