package com.planningapp.dto;

/**
 * Estatísticas dos pontos revelados. A carta café fica fora da média, mínimo e máximo.
 *
 * @param sugestao carta de Fibonacci mais próxima da média (em empate, a maior)
 * @param consenso todos os votos (sem contar café) na mesma carta
 */
public record ResumoPontosDTO(
        int votos,
        int cafes,
        Double media,
        Integer minimo,
        Integer maximo,
        Integer sugestao,
        boolean consenso) {
}
