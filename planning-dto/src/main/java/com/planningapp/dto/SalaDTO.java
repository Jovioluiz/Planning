package com.planningapp.dto;

import java.time.LocalDateTime;

public record SalaDTO(
        Long id,
        String nome,
        String codigo,
        boolean ativa,
        LocalDateTime criadaEm,
        String moderador,
        int quantidadeTarefas) {
}
