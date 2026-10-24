package com.planningapp.service.mapper;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Colunas extras de uma tarefa importada: {@code Map} no DTO, objeto JSON no banco (coluna {@code dados_extras},
 * a mesma do Planning original). A ordem das colunas é preservada, porque o front as mostra nessa ordem.
 */
public final class DadosExtras {

    private DadosExtras() {
    }

    /** {@code null} quando não há colunas, para não gravar {@code {}} à toa. */
    public static String paraJson(Map<String, String> dados) {
        if (dados == null || dados.isEmpty()) {
            return null;
        }
        JsonObjectBuilder json = Json.createObjectBuilder();
        dados.forEach((coluna, valor) -> {
            if (coluna != null && !coluna.isBlank() && valor != null) {
                json.add(coluna.trim(), valor);
            }
        });
        JsonObject objeto = json.build();
        return objeto.isEmpty() ? null : objeto.toString();
    }

    /**
     * Tolerante com o que veio do banco antigo: valores que não são texto (números, booleanos) viram texto, e um
     * JSON inválido vira mapa vazio em vez de quebrar a listagem de tarefas.
     */
    public static Map<String, String> deJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try (JsonReader leitor = Json.createReader(new StringReader(json))) {
            Map<String, String> dados = new LinkedHashMap<>();
            leitor.readObject().forEach((coluna, valor) -> dados.put(coluna, texto(valor)));
            return dados;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private static String texto(JsonValue valor) {
        return switch (valor.getValueType()) {
            case STRING -> ((JsonString) valor).getString();
            case NULL -> "";
            default -> valor.toString();
        };
    }
}
