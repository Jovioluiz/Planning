package com.planningapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VotoHorasRequest(
        @NotNull(message = "Informe as horas")
        @Min(value = 1, message = "As horas devem estar entre 1 e 16")
        @Max(value = 16, message = "As horas devem estar entre 1 e 16")
        Integer horas) {
}
