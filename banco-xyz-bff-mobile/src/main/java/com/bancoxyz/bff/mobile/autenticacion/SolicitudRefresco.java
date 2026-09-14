package com.bancoxyz.bff.mobile.autenticacion;

import jakarta.validation.constraints.NotBlank;

public record SolicitudRefresco(
        @NotBlank(message = "el refresh token es obligatorio") String refreshToken) {
}
