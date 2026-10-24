package com.planningapp.it;

import jakarta.json.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Conexão com o WebSocket de uma sala que guarda os eventos recebidos, em ordem, para o teste conferir. */
public final class SalaWs implements AutoCloseable {

    private final Ouvinte ouvinte;
    private final WebSocket ws;

    private SalaWs(Ouvinte ouvinte, WebSocket ws) {
        this.ouvinte = ouvinte;
        this.ws = ws;
    }

    public static SalaWs conectar(long salaId, String token) {
        return conectar(salaId, token, null);
    }

    public static SalaWs conectar(long salaId, String token, String origem) {
        String url = Api.URL.replaceFirst("^http", "ws") + "/ws/salas/" + salaId + "?token=" + token;
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));
        if (origem != null) {
            builder.header("Origin", origem);
        }
        // A fila é do ouvinte: o servidor pode mandar PRESENCA (ou fechar) antes do buildAsync terminar
        Ouvinte ouvinte = new Ouvinte();
        return new SalaWs(ouvinte, builder.buildAsync(URI.create(url), ouvinte).join());
    }

    /** Espera o próximo evento do tipo, descartando os outros (ex.: PRESENCA de quem entrou). */
    public JsonObject aguardar(String tipo) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            JsonObject evento = ouvinte.eventos.poll(200, TimeUnit.MILLISECONDS);
            if (evento != null && tipo.equals(evento.getString("tipo"))) {
                return evento;
            }
        }
        throw new AssertionError("Evento " + tipo + " não chegou em 10s");
    }

    /** Tipos recebidos até agora (sem PRESENCA), esvaziando a fila. Espera um pouco pelos atrasados. */
    public List<String> tiposRecebidos() throws InterruptedException {
        Thread.sleep(500);
        List<String> tipos = new ArrayList<>();
        JsonObject evento;
        while ((evento = ouvinte.eventos.poll()) != null) {
            if (!"PRESENCA".equals(evento.getString("tipo"))) {
                tipos.add(evento.getString("tipo"));
            }
        }
        return tipos;
    }

    /** Código de fechamento enviado pelo servidor (ex.: 1008 para token inválido). */
    public int codigoDeFechamento() {
        return ouvinte.fechamento.orTimeout(10, TimeUnit.SECONDS).join();
    }

    @Override
    public void close() {
        if (!ws.isOutputClosed()) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "fim").join();
        }
    }

    private static final class Ouvinte implements WebSocket.Listener {

        private final LinkedBlockingQueue<JsonObject> eventos = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> fechamento = new CompletableFuture<>();
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence dados, boolean ultimo) {
            buffer.append(dados);
            if (ultimo) {
                String mensagem = buffer.toString();
                buffer.setLength(0);
                if (!"pong".equals(mensagem)) {
                    eventos.add(Api.ler(mensagem).asJsonObject());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int codigo, String motivo) {
            fechamento.complete(codigo);
            return null;
        }
    }
}
