package com.planningapp.dto;

/**
 * Voto de um usuário numa rodada. {@code pontos}/{@code horas} vêm nulos enquanto estão ocultos
 * para quem consulta; {@code votouPontos}/{@code votouHoras} mostram quem já votou sem revelar o valor.
 * {@code pontos = 0} é a carta café.
 *
 * @param segundosPontos quanto tempo levou para votar nos pontos, desde a liberação da tarefa
 * @param segundosHoras  quanto tempo levou para votar nas horas, desde a liberação das horas
 */
public record VotoDTO(
        String usuario,
        boolean votouPontos,
        Integer pontos,
        boolean votouHoras,
        Integer horas,
        int rodada,
        Long segundosPontos,
        Long segundosHoras) {
}
