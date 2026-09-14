package com.bancoxyz.bff.atm.operaciones;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SolicitudRetiroAtm(
        @NotNull(message = "el monto es obligatorio")
        @Positive(message = "el monto debe ser positivo") BigDecimal monto) {
}
