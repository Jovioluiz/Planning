package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;

/** @param perfil JOGADOR, OBSERVADOR, TESTE ou ADMIN */
public record AlterarPerfilRequest(@NotBlank(message = "Informe o perfil") String perfil) {
}
