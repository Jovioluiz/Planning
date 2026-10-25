package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SalvarConfiguracaoRedmineRequest(
        /** O link da lista de chamados a importar, como aparece no navegador (com os filtros ou o query_id). */
        @NotBlank(message = "O link da consulta do Redmine é obrigatório")
        @Size(max = 4000, message = "O link deve ter no máximo 4000 caracteres")
        String url,

        /** Opcional: identificador do projeto (o que aparece em /projects/{projeto}). */
        @Size(max = 100, message = "O projeto deve ter no máximo 100 caracteres")
        String projeto,

        /** Em branco mantém a chave já gravada (o cliente não a conhece). */
        @Size(max = 100, message = "A chave deve ter no máximo 100 caracteres")
        String chaveApi) {
}
