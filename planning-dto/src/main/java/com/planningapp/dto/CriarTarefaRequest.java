package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CriarTarefaRequest(
        @NotBlank(message = "O número da tarefa é obrigatório")
        @Size(max = 50)
        String numero,

        @NotBlank(message = "O título da tarefa é obrigatório")
        @Size(max = 255)
        String titulo,

        String descricao,

        /** Opcional; agrupa as estatísticas. */
        @Size(max = 50, message = "A sprint deve ter no máximo 50 caracteres")
        String sprint,

        /** Opcional: colunas extras do arquivo importado, exibidas pelo front como colunas dinâmicas. */
        @Size(max = 50, message = "No máximo 50 colunas extras por tarefa")
        Map<String, String> dadosExtras) {
    // Sem construtores extras: o Yasson (JSON-B do WildFly) não desserializa records com mais de um
    // ("Cannot create instance of a record ... Multiple constructors found").
}
