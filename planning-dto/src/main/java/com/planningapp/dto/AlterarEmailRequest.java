package com.planningapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** E-mail do próprio usuário. Vazio ou nulo remove (e aí o "Esqueci minha senha" não chega a ele). */
public record AlterarEmailRequest(
        @Email(message = "E-mail inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email) {
}
