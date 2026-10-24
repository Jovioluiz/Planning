package com.planningapp.security;

import java.util.Set;

/**
 * Nomes dos papéis usados em {@code @RolesAllowed}. Precisam ser constantes de compilação
 * (anotações não aceitam {@code Perfil.ADMIN.name()}), por isso existem aqui além do enum {@code Perfil}.
 */
public final class Papeis {

    public static final String JOGADOR = "JOGADOR";
    public static final String OBSERVADOR = "OBSERVADOR";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER = "SUPER";
    public static final String TESTE = "TESTE";

    public static final Set<String> TODOS = Set.of(JOGADOR, OBSERVADOR, ADMIN, SUPER, TESTE);

    private Papeis() {
    }
}
