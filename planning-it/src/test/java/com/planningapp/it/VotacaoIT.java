package com.planningapp.it;

import com.planningapp.it.Cenario.Sala;
import com.planningapp.it.Cenario.Usuario;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A votação inteira, de ponta a ponta: EJBs, JPA, transações e as regras de ocultação. */
class VotacaoIT {

    private Usuario admin;
    private Usuario ana;
    private Usuario bia;
    private Usuario tess;
    private Usuario obs;
    private Sala sala;
    private String votacao;

    @BeforeEach
    void montarSala() {
        admin = Cenario.usuario("ADMIN");
        ana = Cenario.usuario("JOGADOR");
        bia = Cenario.usuario("JOGADOR");
        tess = Cenario.usuario("TESTE");
        obs = Cenario.usuario("OBSERVADOR");
        sala = Cenario.sala(admin);
        Cenario.entrar(sala, ana, bia, tess, obs);
        long tarefa = Cenario.tarefa(sala, admin, "PP-1");
        votacao = Cenario.votacao(sala, tarefa);
        assertEquals(200, Api.post("/salas/" + sala.id() + "/tarefas/" + tarefa + "/liberar", admin.token(), null).status());
    }

    @Test
    void pontosHorasEHorasDeTesteAteFinalizar() {
        assertEquals(200, Api.post(votacao + "/pontos", ana.token(), "{\"pontos\":5}").status());

        // Antes de revelar: a bia sabe que a ana votou, mas não em quê; a própria ana e o observador veem
        // O JSON-B omite campos nulos: o valor oculto vem sem a chave "pontos"
        assertFalse(votoDe(Api.get(votacao, bia.token()).json(), ana).containsKey("pontos"));
        assertEquals(5, votoDe(Api.get(votacao, ana.token()).json(), ana).getInt("pontos"));
        assertEquals(5, votoDe(Api.get(votacao, obs.token()).json(), ana).getInt("pontos"));

        assertEquals(409, Api.post(votacao + "/pontos", ana.token(), "{\"pontos\":8}").status());
        assertEquals(200, Api.post(votacao + "/pontos", bia.token(), "{\"pontos\":8}").status());
        assertEquals(403, Api.post(votacao + "/revelar-pontos", ana.token(), null).status());

        JsonObject revelado = Api.post(votacao + "/revelar-pontos", admin.token(), null).json();
        JsonObject resumo = revelado.getJsonObject("resumoPontos");
        assertEquals(6.5, resumo.getJsonNumber("media").doubleValue());
        assertEquals(8, resumo.getInt("sugestao"), "empate entre 5 e 8 vai para a maior carta");
        assertFalse(resumo.getBoolean("consenso"));

        Api.post(votacao + "/liberar-horas", admin.token(), null);
        Api.post(votacao + "/horas", ana.token(), "{\"horas\":4}");
        Api.post(votacao + "/horas", bia.token(), "{\"horas\":6}");
        assertEquals(5.0, Api.post(votacao + "/revelar-horas", admin.token(), null).json()
                .getJsonObject("resumoHoras").getJsonNumber("media").doubleValue());

        assertEquals(403, Api.post(votacao + "/horas-teste", ana.token(), "{\"horas\":3}").status(), "só TESTE");
        Api.post(votacao + "/liberar-horas-teste", admin.token(), null);
        assertEquals(200, Api.post(votacao + "/horas-teste", tess.token(), "{\"horas\":3}").status());
        assertEquals(3.0, Api.post(votacao + "/revelar-horas-teste", admin.token(), null).json()
                .getJsonObject("resumoHorasTeste").getJsonNumber("media").doubleValue());

        String tarefas = "/salas/" + sala.id() + "/tarefas/";
        long tarefaId = revelado.getJsonNumber("tarefaId").longValue();
        JsonObject finalizada = Api.post(tarefas + tarefaId + "/finalizar", admin.token(),
                "{\"pontosFinais\":8,\"horasFinais\":5,\"horasTesteFinais\":3}").json();
        assertEquals("ESTIMADA", finalizada.getString("status"));
        assertEquals(8, finalizada.getInt("pontosFinais"));
    }

    @Test
    void novaRodadaRecomecaSemOsVotosAnteriores() {
        Api.post(votacao + "/pontos", ana.token(), "{\"pontos\":13}");

        JsonObject nova = Api.post(votacao + "/nova-rodada", admin.token(), null).json();

        assertEquals(2, nova.getInt("rodada"));
        assertEquals("PONTOS", nova.getString("fase"));
        assertTrue(nova.getJsonArray("votos").isEmpty());
        assertEquals(200, Api.post(votacao + "/pontos", ana.token(), "{\"pontos\":5}").status(), "pode votar de novo");
    }

    @Test
    void soParticipantesVotam() {
        Usuario deFora = Cenario.usuario("JOGADOR");
        Api.Resposta naoEntrou = Api.post(votacao + "/pontos", deFora.token(), "{\"pontos\":5}");
        assertEquals(403, naoEntrou.status());

        String participantes = votacao.replace("/votacao", "/participantes");
        assertEquals(200, Api.delete(participantes + "/" + bia.nome(), admin.token()).status());
        Api.Resposta removida = Api.post(votacao + "/pontos", bia.token(), "{\"pontos\":5}");
        assertEquals(403, removida.status());
        assertEquals("Você foi removido desta votação", removida.mensagem());
    }

    @Test
    void cartaInvalidaEHorasForaDaFaixaSaoRecusadas() {
        assertEquals(422, Api.post(votacao + "/pontos", ana.token(), "{\"pontos\":4}").status());
        Api.post(votacao + "/pontos", ana.token(), "{\"pontos\":5}");
        Api.post(votacao + "/revelar-pontos", admin.token(), null);
        Api.post(votacao + "/liberar-horas", admin.token(), null);
        assertEquals(400, Api.post(votacao + "/horas", ana.token(), "{\"horas\":17}").status());
    }

    private static JsonObject votoDe(JsonObject votacao, Usuario usuario) {
        return votacao.getJsonArray("votos").stream()
                .map(JsonValue::asJsonObject)
                .filter(v -> v.getString("usuario").equals(usuario.nome()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sem voto de " + usuario.nome() + " em " + votacao));
    }
}
