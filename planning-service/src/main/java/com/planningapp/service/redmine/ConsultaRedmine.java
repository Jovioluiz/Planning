package com.planningapp.service.redmine;

import com.planningapp.dto.CriarTarefaRequest;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    /** Datas do Redmine vêm em UTC (ISO-8601); na tela, no fuso da equipe. */
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * @param json   o objeto de cada chamado como veio do Redmine, pelo número (gravado em {@code tarefas.json_redmine})
     * @param total  quantos chamados a consulta tem ao todo (todas as páginas)
     */
    public record Pagina(List<CriarTarefaRequest> chamados, Map<String, String> json, int total) {
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
        List<CriarTarefaRequest> chamados = new ArrayList<>();
        Map<String, String> jsonPorNumero = new LinkedHashMap<>();
        for (JsonObject chamado : resposta.getJsonArray("issues").getValuesAs(JsonObject.class)) {
            CriarTarefaRequest tarefa = paraTarefa(chamado);
            chamados.add(tarefa);
            if (tarefa.numero() != null) {
                jsonPorNumero.put(tarefa.numero(), chamado.toString());
            }
        }
        int total = resposta.containsKey("total_count") ? resposta.getInt("total_count") : chamados.size();
        return new Pagina(List.copyOf(chamados), jsonPorNumero, total);
    }

    /** Um chamado de {@code /issues/{id}.json} como na importação, com o JSON dele como veio do Redmine. */
    public record ChamadoLido(CriarTarefaRequest tarefa, String json) {
    }

    public static ChamadoLido lerTarefa(String json) {
        JsonObject chamado;
        try (JsonReader leitor = Json.createReader(new StringReader(json))) {
            chamado = leitor.readObject().getJsonObject("issue");
        } catch (RuntimeException e) {
            chamado = null;
        }
        if (chamado == null) {
            throw new IllegalArgumentException("Resposta inesperada do Redmine ao buscar o chamado");
        }
        return new ChamadoLido(paraTarefa(chamado), chamado.toString());
    }

    /** Arquivo anexado ao chamado. {@code tamanho} em bytes. */
    public record Anexo(int id, String nome, String tipo, long tamanho) {
    }

    /** Os anexos de {@code /issues/{id}.json?include=attachments}; vazio se o chamado não tem nenhum. */
    public static List<Anexo> lerAnexos(String json) {
        try (JsonReader leitor = Json.createReader(new StringReader(json))) {
            JsonObject chamado = leitor.readObject().getJsonObject("issue");
            List<Anexo> anexos = new ArrayList<>();
            if (chamado.get("attachments") instanceof JsonArray lista) {
                for (JsonValue item : lista) {
                    if (item instanceof JsonObject anexo && anexo.get("id") instanceof JsonNumber id
                            && texto(anexo, "filename") != null) {
                        anexos.add(new Anexo(id.intValue(), texto(anexo, "filename"), texto(anexo, "content_type"),
                                anexo.get("filesize") instanceof JsonNumber tamanho ? tamanho.longValue() : 0));
                    }
                }
            }
            return anexos;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Resposta inesperada do Redmine ao buscar os anexos do chamado");
        }
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

    // ─── Envio da estimativa ao chamado ─────────────────────────────────────────────────────────────────────────

    /** Campo personalizado de um chamado. {@code valor} nulo: vazio no Redmine. */
    public record CampoPersonalizado(int id, String nome, String valor) {
    }

    /** Situação (status) do Redmine. O nome vem sem os espaços que às vezes sobram no cadastro. */
    public record Situacao(int id, String nome) {
    }

    /** O que o envio da estimativa precisa de {@code /issues/{id}.json}: para achar os campos e conferir. */
    public record Chamado(Double tempoEstimado, Situacao situacao, List<CampoPersonalizado> campos) {

        /** Nulo quando o campo não vale para o tipo (ou o projeto) do chamado. */
        public CampoPersonalizado campo(int id) {
            return campos.stream().filter(c -> c.id() == id).findFirst().orElse(null);
        }
    }

    public static Chamado lerChamado(String json) {
        try (JsonReader leitor = Json.createReader(new StringReader(json))) {
            JsonObject chamado = leitor.readObject().getJsonObject("issue");
            Double tempo = chamado.get("estimated_hours") instanceof JsonNumber horas ? horas.doubleValue() : null;
            Situacao situacao = chamado.get("status") instanceof JsonObject status && status.get("id") instanceof JsonNumber id
                    ? new Situacao(id.intValue(), texto(status, "name") == null ? "" : texto(status, "name").trim())
                    : null;
            List<CampoPersonalizado> campos = new ArrayList<>();
            if (chamado.get("custom_fields") instanceof JsonArray personalizados) {
                for (JsonValue item : personalizados) {
                    if (item instanceof JsonObject campo && campo.get("id") instanceof JsonNumber id
                            && texto(campo, "name") != null) {
                        campos.add(new CampoPersonalizado(id.intValue(), texto(campo, "name"),
                                valorPersonalizado(campo.get("value"))));
                    }
                }
            }
            return new Chamado(tempo, situacao, campos);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Resposta inesperada do Redmine ao buscar o chamado");
        }
    }

    /**
     * Corpo do {@code PUT /issues/{id}.json}: {@code {"issue":{"estimated_hours":5,"status_id":8,"custom_fields":
     * [{"id":3,"value":"8"}]}}}. Horas ou situação nulas não vão (ficam como estão no chamado).
     */
    public static String corpoAtualizacao(Integer horas, Integer situacaoId, Map<Integer, String> camposPersonalizados) {
        var issue = Json.createObjectBuilder();
        if (horas != null) {
            issue.add("estimated_hours", horas);
        }
        if (situacaoId != null) {
            issue.add("status_id", situacaoId);
        }
        if (!camposPersonalizados.isEmpty()) {
            var campos = Json.createArrayBuilder();
            camposPersonalizados.forEach((id, valor) -> campos.add(Json.createObjectBuilder().add("id", id).add("value", valor)));
            issue.add("custom_fields", campos);
        }
        return Json.createObjectBuilder().add("issue", issue).build().toString();
    }

    /** Mensagens de um 422 do Redmine ({@code {"errors":["..."]}}); vazio se o corpo não for esse. */
    public static List<String> lerErros(String json) {
        try (JsonReader leitor = Json.createReader(new StringReader(json == null ? "" : json))) {
            JsonValue erros = leitor.readObject().get("errors");
            return erros instanceof JsonArray lista
                    ? lista.stream().map(ConsultaRedmine::valorSimples).filter(e -> e != null && !e.isBlank()).toList()
                    : List.of();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * Todos os campos que o {@code /issues.json} traz, na ordem em que o painel de detalhe da tarefa os mostra: os
     * principais (os da exportação CSV do Redmine) primeiro, depois os demais e os campos personalizados pelo nome
     * que têm no Redmine. A descrição não entra: tem coluna própria.
     */
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
        extras.put("Tempo estimado", numero(chamado, "estimated_hours"));
        extras.put("Projeto", nome(chamado, "project"));
        extras.put("Categoria", nome(chamado, "category"));
        extras.put("Autor", nome(chamado, "author"));
        extras.put("Tarefa pai", chamado.get("parent") instanceof JsonObject pai && pai.containsKey("id")
                ? "#" + pai.get("id") : null);
        extras.put("Início", data(texto(chamado, "start_date")));
        extras.put("Data prevista", data(texto(chamado, "due_date")));
        String terminado = numero(chamado, "done_ratio");
        extras.put("% Terminado", terminado == null ? null : terminado + "%");
        String total = numero(chamado, "total_estimated_hours");
        if (total != null && !total.equals(extras.get("Tempo estimado"))) {
            extras.put("Tempo estimado total", total);
        }
        extras.put("Tempo gasto", numero(chamado, "spent_hours"));
        if (chamado.containsKey("is_private") && chamado.get("is_private") == JsonValue.TRUE) {
            extras.put("Privada", "Sim");
        }
        extras.put("Criada em", dataHora(texto(chamado, "created_on")));
        extras.put("Atualizada em", dataHora(texto(chamado, "updated_on")));
        extras.put("Fechada em", dataHora(texto(chamado, "closed_on")));
        if (chamado.get("custom_fields") instanceof JsonArray personalizados) {
            for (JsonValue item : personalizados) {
                if (item instanceof JsonObject campo && texto(campo, "name") != null) {
                    // Um campo personalizado com o nome de um padrão não o sobrescreve
                    extras.putIfAbsent(texto(campo, "name").trim(), valorPersonalizado(campo.get("value")));
                }
            }
        }
        // Vazios também: o painel de detalhe mostra o campo, para o moderador ver o que falta preencher
        extras.replaceAll((campo, valor) -> valor == null ? "" : valor);

        String sprint = versao == null || versao.length() <= MAXIMO_SPRINT ? versao : versao.substring(0, MAXIMO_SPRINT);
        return new CriarTarefaRequest(numero, titulo, texto(chamado, "description"), sprint, extras);
    }

    /** Campos como {@code tracker} e {@code status} vêm como {@code {"id": 1, "name": "Bug"}}. */
    private static String nome(JsonObject chamado, String campo) {
        JsonValue valor = chamado.get(campo);
        return valor instanceof JsonObject objeto ? texto(objeto, "name") : null;
    }

    private static String numero(JsonObject chamado, String campo) {
        return chamado.get(campo) instanceof JsonNumber valor ? valor.toString() : null;
    }

    /** Campo personalizado de múltiplos valores vem como lista: vira "a, b". */
    private static String valorPersonalizado(JsonValue valor) {
        if (valor instanceof JsonArray lista) {
            return lista.stream()
                    .map(ConsultaRedmine::valorSimples)
                    .filter(v -> v != null && !v.isBlank())
                    .collect(Collectors.joining(", "));
        }
        return valorSimples(valor);
    }

    private static String valorSimples(JsonValue valor) {
        if (valor == null || valor.getValueType() == JsonValue.ValueType.NULL) {
            return null;
        }
        return valor instanceof JsonString texto ? texto.getString() : valor.toString();
    }

    /** {@code 2025-10-14} → {@code 14/10/2025}; o que não for data fica como veio. */
    private static String data(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return LocalDate.parse(valor).format(DATA);
        } catch (DateTimeParseException e) {
            return valor;
        }
    }

    /** {@code 2025-10-14T12:30:00Z} → {@code 14/10/2025 09:30} (no {@link #FUSO}). */
    private static String dataHora(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor).atZoneSameInstant(FUSO).format(DATA_HORA);
        } catch (DateTimeParseException e) {
            return valor;
        }
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
