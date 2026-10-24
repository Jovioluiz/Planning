package com.planningapp.dto;

import jakarta.validation.constraints.NotNull;

/** {@code pontos}: uma carta de Fibonacci (1, 2, 3, 5, 8, 13, 21) ou 0 para a carta café. */
public record VotoPontosRequest(
        @NotNull(message = "Escolha uma carta")
        Integer pontos) {
}
