package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrarUsuarioRequest(
        @NotBlank(message = "Informe o usuário")
        @Size(min = 3, max = 50, message = "O usuário deve ter entre 3 e 50 caracteres")
        // Letras com acento e espaço, como o Planning original aceitava (os usuários migrados já existem assim)
        @Pattern(regexp = "[\\p{L}\\p{N} ._-]+", message = "O usuário só pode ter letras, números, espaço, ponto, hífen e sublinhado")
        String usuario,

        @NotBlank(message = "Informe a senha")
        @Size(min = 6, max = 100, message = "A senha deve ter entre 6 e 100 caracteres")
        String senha,

        /** JOGADOR, OBSERVADOR, TESTE ou ADMIN (este último só se ainda não existir nenhum). */
        @NotBlank(message = "Informe o perfil")
        String perfil) {
}
