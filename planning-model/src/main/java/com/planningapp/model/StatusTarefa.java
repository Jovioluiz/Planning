package com.planningapp.model;

public enum StatusTarefa {
    FILA,
    LIBERADA,
    ESTIMADA,
    /** Encerrada sem estimativa pelo moderador. Pode ser liberada de novo, numa rodada nova. */
    PULADA
}
