package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Informe o usuário")
        String usuario,

        @NotBlank(message = "Informe a senha")
        String senha) {
}
