package com.planningapp.service;

import java.util.Locale;

/**
 * Em que ambiente o servidor roda, pela variável {@code PLANNING_AMBIENTE} ("producao", o padrão, ou "testes").
 * A hospedagem local define a variável junto com o banco ({@code banco.ps1}). Serve para o front mostrar a faixa
 * de testes e para marcar os e-mails enviados fora da produção.
 */
public final class Ambiente {

    public static final String PRODUCAO = "producao";

    private Ambiente() {
    }

    public static String atual() {
        String valor = System.getenv("PLANNING_AMBIENTE");
        return valor == null || valor.isBlank() ? PRODUCAO : valor.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean producao() {
        return PRODUCAO.equals(atual());
    }
}
