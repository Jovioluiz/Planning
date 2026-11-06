package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedido do link de redefinição de senha, pela tela de login: o nome de usuário ou o e-mail cadastrado. */
public record EsqueciSenhaRequest(
        @NotBlank(message = "Informe o usuário ou o e-mail")
        @Size(max = 150, message = "Informe o usuário ou o e-mail")
        String usuarioOuEmail) {
}
