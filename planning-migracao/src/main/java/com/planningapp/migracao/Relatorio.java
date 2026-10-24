package com.planningapp.migracao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** O que foi migrado, o que ficou de fora e por quê. */
public final class Relatorio {

    private final Map<String, Integer> migrados = new LinkedHashMap<>();
    private final List<String> avisos = new ArrayList<>();

    void contar(String tabela, int quantidade) {
        migrados.merge(tabela, quantidade, Integer::sum);
    }

    void avisar(String aviso) {
        avisos.add(aviso);
    }

    public int migrados(String tabela) {
        return migrados.getOrDefault(tabela, 0);
    }

    public Map<String, Integer> migrados() {
        return Map.copyOf(migrados);
    }

    public List<String> avisos() {
        return List.copyOf(avisos);
    }

    @Override
    public String toString() {
        StringBuilder texto = new StringBuilder("Migrados:\n");
        migrados.forEach((tabela, n) -> texto.append(String.format("  %-22s %d%n", tabela, n)));
        if (avisos.isEmpty()) {
            texto.append("Nenhum aviso.\n");
        } else {
            texto.append("Avisos (").append(avisos.size()).append("):\n");
            avisos.forEach(a -> texto.append("  - ").append(a).append('\n'));
        }
        return texto.toString();
    }
}
