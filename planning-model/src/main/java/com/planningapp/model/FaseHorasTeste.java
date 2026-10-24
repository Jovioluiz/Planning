package com.planningapp.model;

/**
 * Trilha opcional e paralela de horas de teste (perfil TESTE). Independe da {@link FaseVotacao}:
 * o moderador pode liberá-la em qualquer fase da votação principal. Nula = ainda não liberada.
 */
public enum FaseHorasTeste {
    /** Testadores votam; votos ocultos. */
    VOTACAO,
    /** Moderador revelou as horas de teste. */
    REVELADA
}
