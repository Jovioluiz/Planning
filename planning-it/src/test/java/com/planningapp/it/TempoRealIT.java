package com.planningapp.it;

import com.planningapp.it.Cenario.Sala;
import com.planningapp.it.Cenario.Usuario;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** WebSocket da sala: autenticação, checagem de origem e os eventos disparados pelos EJBs (CDI AFTER_SUCCESS). */
class TempoRealIT {

    @Test
    void tokenInvalidoFechaComViolacaoDePolitica() {
        Usuario admin = Cenario.usuario("ADMIN");
        Sala sala = Cenario.sala(admin);

        try (SalaWs ws = SalaWs.conectar(sala.id(), "token-qualquer")) {
            assertEquals(1008, ws.codigoDeFechamento());
        }
    }

    @Test
    void origemDeOutroSiteEhRecusadaNoHandshake() {
        Usuario admin = Cenario.usuario("ADMIN");
        Sala sala = Cenario.sala(admin);

        assertThrows(CompletionException.class, () -> SalaWs.conectar(sala.id(), admin.token(), "https://site-malicioso.example"));
    }

    @Test
    void votosChegamEmOrdemEOAvisoDeTodosVotaramSaiUmaVez() throws Exception {
        Usuario admin = Cenario.usuario("ADMIN");
        Usuario ana = Cenario.usuario("JOGADOR");
        Usuario bia = Cenario.usuario("JOGADOR");
        Sala sala = Cenario.sala(admin);
        Cenario.entrar(sala, ana, bia);
        long tarefa = Cenario.tarefa(sala, admin, "PP-1");

        try (SalaWs moderador = SalaWs.conectar(sala.id(), admin.token());
             SalaWs wsAna = SalaWs.conectar(sala.id(), ana.token());
             SalaWs wsBia = SalaWs.conectar(sala.id(), bia.token())) {
            // A presença é registrada antes do PRESENCA ser enviado: depois disto, as duas contam como online
            wsAna.aguardar("PRESENCA");
            wsBia.aguardar("PRESENCA");

            Api.post("/salas/" + sala.id() + "/tarefas/" + tarefa + "/liberar", admin.token(), null);
            moderador.aguardar("TAREFA_LIBERADA");
            moderador.tiposRecebidos();

            Api.post(Cenario.votacao(sala, tarefa) + "/pontos", ana.token(), "{\"pontos\":5}");
            Api.post(Cenario.votacao(sala, tarefa) + "/pontos", bia.token(), "{\"pontos\":8}");

            // Os dois eventos do último voto vão num lote único, para o "todos votaram" não chegar antes do voto
            assertEquals(List.of("VOTO_REGISTRADO", "VOTO_REGISTRADO", "TODOS_VOTARAM"), moderador.tiposRecebidos());
        }
    }

    @Test
    void eventoDeVotoNaoRevelaOValor() throws Exception {
        Usuario admin = Cenario.usuario("ADMIN");
        Usuario ana = Cenario.usuario("JOGADOR");
        Sala sala = Cenario.sala(admin);
        Cenario.entrar(sala, ana);
        long tarefa = Cenario.tarefa(sala, admin, "PP-1");
        Api.post("/salas/" + sala.id() + "/tarefas/" + tarefa + "/liberar", admin.token(), null);

        try (SalaWs moderador = SalaWs.conectar(sala.id(), admin.token())) {
            moderador.aguardar("PRESENCA");
            Api.post(Cenario.votacao(sala, tarefa) + "/pontos", ana.token(), "{\"pontos\":13}");

            JsonObject evento = moderador.aguardar("VOTO_REGISTRADO");
            assertEquals(ana.nome(), evento.getString("usuario"));
            assertFalse(evento.containsKey("pontos"), "o valor do voto não pode vazar: " + evento);
            assertFalse(evento.getJsonObject("tarefa").containsKey("pontosFinais"), evento::toString);
        }
    }
}
