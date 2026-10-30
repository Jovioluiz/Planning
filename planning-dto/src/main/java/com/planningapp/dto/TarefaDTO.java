package com.planningapp.dto;

import java.time.Instant;
import java.util.Map;

/**
 * @param fase             fase da votação (PONTOS, PONTOS_REVELADOS, HORAS, HORAS_REVELADAS); nula fora da votação
 * @param faseHorasTeste   trilha de horas de teste (VOTACAO, REVELADA); nula enquanto não liberada
 * @param rodadaAtual      começa em 1 e sobe a cada nova rodada
 * @param encerradaEm      quando foi finalizada ou pulada
 * @param horasLiberadasEm quando a votação de horas da rodada atual foi aberta
 * @param dadosExtras      colunas extras do arquivo importado (exibidas como colunas dinâmicas no front)
 * @param doRedmine        importada do Redmine: ao finalizar, o front envia a estimativa ao chamado
 */
public record TarefaDTO(
        Long id,
        String numero,
        String titulo,
        String descricao,
        String sprint,
        String status,
        String fase,
        String faseHorasTeste,
        int rodadaAtual,
        Integer pontosFinais,
        Integer horasFinais,
        Integer horasTesteFinais,
        Long salaId,
        Instant liberadaEm,
        Instant encerradaEm,
        Instant horasLiberadasEm,
        Map<String, String> dadosExtras,
        boolean doRedmine) {
}
