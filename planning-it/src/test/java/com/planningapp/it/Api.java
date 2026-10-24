package com.planningapp.it;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/** Cliente HTTP mínimo para a API em {@code /planning/api}, sobre java.net.http. */
public final class Api {

    public static final String URL = System.getProperty("it.url", "http://localhost:8380/planning");
    public static final String SUPER_USUARIO = System.getProperty("it.super.usuario", "super-it");
    public static final String SUPER_SENHA = System.getProperty("it.super.senha", "senha-do-super-it");

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** Resposta com o status e o corpo; {@link #json()} e {@link #lista()} interpretam o corpo. */
    public record Resposta(int status, String corpo, HttpResponse<String> http) {

        public JsonObject json() {
            return ler(corpo).asJsonObject();
        }

        public JsonArray lista() {
            return ler(corpo).asJsonArray();
        }

        public String mensagem() {
            return json().getString("mensagem");
        }

        public String header(String nome) {
            return http.headers().firstValue(nome).orElse(null);
        }

        @Override
        public String toString() {
            return status + " " + corpo;
        }
    }

    private Api() {
    }

    public static Resposta get(String caminho, String token) {
        return enviar(requisicao(caminho, token).GET());
    }

    public static Resposta post(String caminho, String token, String json) {
        return enviar(requisicao(caminho, token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json == null ? "{}" : json)));
    }

    public static Resposta postCsv(String caminho, String token, String csv) {
        return enviar(requisicao(caminho, token).header("Content-Type", "text/csv; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(csv)));
    }

    public static Resposta put(String caminho, String token, String json) {
        return enviar(requisicao(caminho, token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    public static Resposta delete(String caminho, String token) {
        return enviar(requisicao(caminho, token).DELETE());
    }

    /** Para os testes de CORS, que precisam de headers e métodos específicos. */
    public static Resposta enviar(HttpRequest.Builder requisicao) {
        try {
            HttpResponse<String> resposta = HTTP.send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
            return new Resposta(resposta.statusCode(), resposta.body(), resposta);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao chamar a API em " + URL + ". O WildFly subiu?", e);
        }
    }

    public static HttpRequest.Builder requisicao(String caminho, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(URL + "/api" + caminho)).timeout(Duration.ofSeconds(30));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    public static String login(String usuario, String senha) {
        Resposta resposta = post("/auth/login", null, "{\"usuario\":\"" + usuario + "\",\"senha\":\"" + senha + "\"}");
        if (resposta.status() != 200) {
            throw new IllegalStateException("Login de " + usuario + " falhou: " + resposta);
        }
        return resposta.json().getString("token");
    }

    /** Nomes únicos: todos os testes compartilham o mesmo servidor e o mesmo banco. */
    public static String unico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static JsonValue ler(String json) {
        try (var leitor = Json.createReader(new StringReader(json))) {
            return leitor.readValue();
        }
    }
}
