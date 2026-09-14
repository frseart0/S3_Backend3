package com.bancoxyz.bff.mobile.autenticacion;

import jakarta.validation.constraints.NotBlank;

public record CredencialesMobile(
        @NotBlank(message = "el usuario es obligatorio") String usuario,
        @NotBlank(message = "la contrasena es obligatoria") String password) {
}
