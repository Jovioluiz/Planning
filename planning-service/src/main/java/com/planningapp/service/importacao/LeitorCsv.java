package com.planningapp.service.importacao;

import java.util.ArrayList;
import java.util.List;

/**
 * Leitor de CSV no formato RFC 4180: campos entre aspas podem ter separador, quebra de linha e aspas
 * duplicadas ({@code ""}). O separador é detectado pelo cabeçalho: {@code ;} (padrão do Excel em pt-BR),
 * {@code ,} ou tab. Linhas em branco são ignoradas.
 */
public final class LeitorCsv {

    /** @param linha linha do arquivo onde o registro começa (1 = cabeçalho), para as mensagens de erro */
    public record Registro(int linha, List<String> campos) {
    }

    private LeitorCsv() {
    }

    public static List<Registro> ler(String conteudo) {
        String texto = conteudo.startsWith("﻿") ? conteudo.substring(1) : conteudo; // BOM do Excel
        char separador = detectarSeparador(texto);

        List<Registro> registros = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreAspas = false;
        int linha = 1;
        int inicioDoRegistro = 1;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreAspas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else if (c == '"') {
                    entreAspas = false;
                } else {
                    if (c == '\n') {
                        linha++;
                    }
                    campo.append(c);
                }
            } else if (c == '"' && campo.isEmpty()) {
                entreAspas = true;
            } else if (c == separador) {
                campos.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                campos.add(campo.toString());
                campo.setLength(0);
                adicionar(registros, inicioDoRegistro, campos);
                campos = new ArrayList<>();
                linha++;
                inicioDoRegistro = linha;
            } else {
                campo.append(c);
            }
        }
        if (entreAspas) {
            throw new IllegalArgumentException("Aspas sem fechamento no registro que começa na linha " + inicioDoRegistro);
        }
        campos.add(campo.toString());
        adicionar(registros, inicioDoRegistro, campos);
        return registros;
    }

    private static void adicionar(List<Registro> registros, int linha, List<String> campos) {
        boolean emBranco = campos.stream().allMatch(String::isBlank);
        if (!emBranco) {
            registros.add(new Registro(linha, List.copyOf(campos)));
        }
    }

    /** O mais frequente na primeira linha; na dúvida, {@code ;}. */
    private static char detectarSeparador(String texto) {
        int fim = texto.indexOf('\n');
        String cabecalho = fim < 0 ? texto : texto.substring(0, fim);
        long pontoEVirgula = cabecalho.chars().filter(c -> c == ';').count();
        long virgula = cabecalho.chars().filter(c -> c == ',').count();
        long tab = cabecalho.chars().filter(c -> c == '\t').count();
        if (tab > pontoEVirgula && tab > virgula) {
            return '\t';
        }
        return virgula > pontoEVirgula ? ',' : ';';
    }
}
