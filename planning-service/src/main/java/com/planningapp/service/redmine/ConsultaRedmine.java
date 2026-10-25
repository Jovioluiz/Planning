package com.planningapp.service.redmine;

import com.planningapp.dto.CriarTarefaRequest;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Transforma o link de uma lista de chamados do Redmine (como aparece no navegador) na chamada da API REST
 * ({@code /issues.json}) e lê a resposta. Sem dependência de container, para ter testes unitários; quem faz a
 * requisição é o {@link com.planningapp.service.RedmineService}.
 */
public final class ConsultaRedmine {

    /** O máximo que o Redmine devolve por página. */
    public static final int POR_PAGINA = 100;

    /** Parâmetros do link que a paginação (ou a chave) define, e não o usuário. */
    private static final Set<String> PARAMETROS_DESCARTADOS = Set.of("limit", "offset", "page", "per_page", "key", "format");

    private static final Set<String> EXTENSOES = Set.of(".json", ".csv", ".pdf", ".atom", ".xml");

    private static final int MAXIMO_SPRINT = 50;

    public record Pagina(List<CriarTarefaRequest> chamados, int total) {
    }

    private ConsultaRedmine() {
    }

    /**
     * Endereço do Redmine a partir do link da consulta: o que vem antes de {@code /projects/...} ou de
     * {@code /issues} ({@code https://redmine/projects/erp/issues?query_id=1} → {@code https://redmine}).
     */
    public static String baseDaConsulta(String link) {
        String caminho = semConsulta(link);
        if (!caminho.endsWith("/issues")) {
            throw new IllegalArgumentException("O link precisa ser de uma lista de chamados do Redmine (termina em "
                    + "/issues), por exemplo https://redmine.empresa.com.br/projects/erp/issues?query_id=123");
        }
        int projetos = caminho.indexOf("/projects/");
        return normalizarBase(projetos >= 0 ? caminho.substring(0, projetos)
                : caminho.substring(0, caminho.length() - "/issues".length()));
    }

    /** O link sem consulta, fragmento, extensão ({@code .csv}...) e barra no fim. */
    private static String semConsulta(String link) {
        String caminho = link == null ? "" : link.trim();
        for (char separador : new char[]{'#', '?'}) {
            int posicao = caminho.indexOf(separador);
            if (posicao >= 0) {
                caminho = caminho.substring(0, posicao);
            }
        }
        while (caminho.endsWith("/")) {
            caminho = caminho.substring(0, caminho.length() - 1);
        }
        for (String extensao : EXTENSOES) {
            if (caminho.toLowerCase(Locale.ROOT).endsWith(extensao)) {
                caminho = caminho.substring(0, caminho.length() - extensao.length());
            }
        }
        return caminho;
    }

    /** URL base sem barra no fim; só http e https. */
    public static String normalizarBase(String url) {
        String base = url == null ? "" : url.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(base);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Link do Redmine inválido: " + url);
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!esquema.equals("http") && !esquema.equals("https") || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("O link precisa começar com http:// ou https:// "
                    + "(ex.: https://redmine.empresa.com.br/projects/erp/issues?query_id=123)");
        }
        return base;
    }

    /**
     * Página {@code offset} da consulta, em JSON. O link precisa ser do Redmine configurado e de uma lista de
     * chamados: {@code {base}/issues?...} ou {@code {base}/projects/{projeto}/issues?...}, com os filtros ou o
     * {@code query_id} de uma consulta salva. No link sem projeto, o projeto configurado (se houver) entra como
     * {@code project_id}.
     */
    public static URI paginaJson(String base, String projeto, String link, int offset) {
        String texto = link == null ? "" : link.trim();
        int fragmento = texto.indexOf('#');
        if (fragmento >= 0) {
            texto = texto.substring(0, fragmento);
        }
        if (!texto.toLowerCase(Locale.ROOT).startsWith(base.toLowerCase(Locale.ROOT) + "/")) {
            throw new IllegalArgumentException("O link precisa ser do Redmine configurado (" + base + ")");
        }
        String resto = texto.substring(base.length());
        int interrogacao = resto.indexOf('?');
        String caminho = interrogacao < 0 ? resto : resto.substring(0, interrogacao);
        String consulta = interrogacao < 0 ? "" : resto.substring(interrogacao + 1);

        while (caminho.endsWith("/")) {
            caminho = caminho.substring(0, caminho.length() - 1);
        }
        for (String extensao : EXTENSOES) {
            if (caminho.toLowerCase(Locale.ROOT).endsWith(extensao)) {
                caminho = caminho.substring(0, caminho.length() - extensao.length());
            }
        }
        if (!caminho.endsWith("/issues")) {
            throw new IllegalArgumentException("O link precisa ser de uma lista de chamados (termina em /issues), "
                    + "por exemplo " + base + "/projects/erp/issues?query_id=123");
        }

        List<String> parametros = new ArrayList<>();
        boolean temProjeto = false;
        for (String parametro : consulta.split("&")) {
            if (parametro.isBlank()) {
                continue;
            }
            String nome = parametro.split("=", 2)[0];
            if (PARAMETROS_DESCARTADOS.contains(nome.toLowerCase(Locale.ROOT))) {
                continue;
            }
            temProjeto |= nome.equals("project_id");
            parametros.add(codificar(parametro));
        }
        if (caminho.equals("/issues") && !temProjeto && projeto != null && !projeto.isBlank()) {
            parametros.add("project_id=" + URLEncoder.encode(projeto.trim(), StandardCharsets.UTF_8));
        }
        parametros.add("limit=" + POR_PAGINA);
        parametros.add("offset=" + offset);
        return URI.create(base + codificar(caminho) + ".json?" + String.join("&", parametros));
    }

    /**
     * Uma página de {@code /issues.json}. Cada chamado vira uma tarefa: número = id, título = assunto,
     * sprint = versão. Os campos que o moderador costuma olhar vão para {@code dadosExtras} (colunas da fila),
     * com os nomes da exportação CSV do Redmine em português.
     */
    public static Pagina lerPagina(String json) {
        JsonObject resposta;
        try (JsonReader leitor = Json.createReader(new StringReader(json))) {
            resposta = leitor.readObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Resposta inesperada do Redmine (não é o JSON de uma lista de chamados)");
        }
        if (!resposta.containsKey("issues") || resposta.get("issues").getValueType() != JsonValue.ValueType.ARRAY) {
            throw new IllegalArgumentException("Resposta inesperada do Redmine (sem a lista de chamados)");
        }
        List<CriarTarefaRequest> chamados = resposta.getJsonArray("issues").getValuesAs(JsonObject.class).stream()
                .map(ConsultaRedmine::paraTarefa)
                .toList();
        int total = resposta.containsKey("total_count") ? resposta.getInt("total_count") : chamados.size();
        return new Pagina(chamados, total);
    }

    /** Nome do usuário dono da chave, de {@code /users/current.json}. */
    public static String lerUsuarioAtual(String json) {
        try (JsonReader leitor = Json.createReader(new StringReader(json))) {
            JsonObject usuario = leitor.readObject().getJsonObject("user");
            String nome = Stream.of(texto(usuario, "firstname"), texto(usuario, "lastname"))
                    .filter(parte -> parte != null && !parte.isBlank())
                    .collect(Collectors.joining(" "));
            return nome.isEmpty() ? texto(usuario, "login") : nome;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Resposta inesperada do Redmine ao identificar o usuário da chave");
        }
    }

    /** Nome do projeto, de {@code /projects/{projeto}.json}. */
    public static String lerProjeto(String json) {
        try (JsonReader leitor = Json.createReader(new StringReader(json))) {
            return texto(leitor.readObject().getJsonObject("project"), "name");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Resposta inesperada do Redmine ao buscar o projeto");
        }
    }

    private static CriarTarefaRequest paraTarefa(JsonObject chamado) {
        String numero = chamado.containsKey("id") ? chamado.get("id").toString() : null;
        String titulo = texto(chamado, "subject");
        String versao = nome(chamado, "fixed_version");

        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("Título", titulo);
        extras.put("Tipo", nome(chamado, "tracker"));
        extras.put("Situação", nome(chamado, "status"));
        extras.put("Prioridade", nome(chamado, "priority"));
        extras.put("Atribuído para", nome(chamado, "assigned_to"));
        extras.put("Versão", versao);
        if (chamado.get("estimated_hours") instanceof JsonNumber horas) {
            extras.put("Tempo estimado", horas.toString());
        }
        extras.values().removeIf(v -> v == null || v.isBlank());

        String sprint = versao == null || versao.length() <= MAXIMO_SPRINT ? versao : versao.substring(0, MAXIMO_SPRINT);
        return new CriarTarefaRequest(numero, titulo, texto(chamado, "description"), sprint,
                extras.isEmpty() ? null : extras);
    }

    /** Campos como {@code tracker} e {@code status} vêm como {@code {"id": 1, "name": "Bug"}}. */
    private static String nome(JsonObject chamado, String campo) {
        JsonValue valor = chamado.get(campo);
        return valor instanceof JsonObject objeto ? texto(objeto, "name") : null;
    }

    private static String texto(JsonObject objeto, String campo) {
        if (objeto == null || !objeto.containsKey(campo) || objeto.isNull(campo)) {
            return null;
        }
        JsonValue valor = objeto.get(campo);
        return valor.getValueType() == JsonValue.ValueType.STRING ? objeto.getString(campo) : valor.toString();
    }

    /**
     * Links copiados do navegador às vezes trazem {@code f[]=status_id} ou espaços sem codificar, que o
     * {@link URI} recusa. O que já está codificado ({@code %5B}) fica como está.
     */
    private static String codificar(String trecho) {
        StringBuilder resultado = new StringBuilder();
        for (char c : trecho.toCharArray()) {
            switch (c) {
                case '[' -> resultado.append("%5B");
                case ']' -> resultado.append("%5D");
                case ' ' -> resultado.append("%20");
                case '|' -> resultado.append("%7C");
                case '"' -> resultado.append("%22");
                default -> resultado.append(c);
            }
        }
        return resultado.toString();
    }
}
