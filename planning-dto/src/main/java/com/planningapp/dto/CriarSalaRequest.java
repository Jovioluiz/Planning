package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarSalaRequest(
        @NotBlank(message = "O nome da sala é obrigatório")
        @Size(max = 100, message = "O nome da sala deve ter no máximo 100 caracteres")
        String nome) {
}
