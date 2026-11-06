package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nova senha a partir do link do e-mail ({@code token} é o que veio no link). */
public record RedefinirSenhaPorLinkRequest(
        @NotBlank(message = "Link de redefinição inválido")
        @Size(max = 200, message = "Link de redefinição inválido")
        String token,

        @NotBlank(message = "Informe a nova senha")
        @Size(min = 6, max = 100, message = "A senha deve ter entre 6 e 100 caracteres")
        String novaSenha) {
}
