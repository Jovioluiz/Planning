package com.planningapp.model;

/**
 * Fases de uma tarefa em votação ({@link StatusTarefa#LIBERADA}), na ordem em que acontecem.
 * Equivale ao {@code estadoVotacao} do front do Planning original: pontos → aguardando_horas → horas.
 */
public enum FaseVotacao {
    /** Jogadores votam nos pontos; votos ocultos. */
    PONTOS,
    /** Moderador revelou os pontos; aguardando liberar horas. */
    PONTOS_REVELADOS,
    /** Jogadores votam nas horas; votos de horas ocultos. */
    HORAS,
    /** Moderador revelou as horas; pronto para finalizar ou nova rodada. */
    HORAS_REVELADAS;

    public boolean pontosRevelados() {
        return compareTo(PONTOS_REVELADOS) >= 0;
    }

    public boolean horasReveladas() {
        return this == HORAS_REVELADAS;
    }
}
