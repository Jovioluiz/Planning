package com.planningapp.model;

/** Mesmos perfis do Planning original. O nome de cada um é o papel usado no JWT e no {@code @RolesAllowed}. */
public enum Perfil {
    JOGADOR,
    OBSERVADOR,
    ADMIN,
    SUPER,
    TESTE
}
