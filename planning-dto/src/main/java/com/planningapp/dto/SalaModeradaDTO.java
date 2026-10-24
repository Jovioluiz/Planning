package com.planningapp.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sala na lista "minhas salas" do moderador. Diferente do {@link SalaDTO} (que é público pelo código), traz
 * quem já entrou na sala, como o {@code SalaDTO} do Planning original.
 */
public record SalaModeradaDTO(
        Long id,
        String nome,
        String codigo,
        boolean ativa,
        LocalDateTime criadaEm,
        int quantidadeTarefas,
        int tarefasPendentes,
        List<String> membros) {
}
