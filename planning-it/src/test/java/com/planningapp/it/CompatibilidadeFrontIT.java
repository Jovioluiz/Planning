package com.planningapp.it;

import com.planningapp.it.Cenario.Sala;
import com.planningapp.it.Cenario.Usuario;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O que o backend ganhou para o front do Planning original funcionar (ver DECISOES-MIGRACAO.md): uma tarefa por
 * id, "minhas salas" com membros, lista de online, rodada anterior, tempo de voto e a inativação automática da
 * sala quando o moderador sai (com SALA_INATIVACAO_SEGUNDOS=2 no servidor de teste).
 */
class CompatibilidadeFrontIT {

    @Test
    void tarefaPorIdTrazAsColunasExtrasDaImportacao() {
        Usuario admin = Cenario.usuario("ADMIN");
        Sala sala = Cenario.sala(admin);
        JsonObject importada = Api.post("/salas/" + sala.id() + "/tarefas/importar", admin.token(),
                        "[{\"numero\":\"7\",\"titulo\":\"Login\",\"dadosExtras\":{\"Prioridade\":\"Alta\",\"Área\":\"Web\"}}]")
                .json().getJsonArray("importadas").getJsonObject(0);

        JsonObject tarefa = Api.get("/salas/" + sala.id() + "/tarefas/" + importada.getJsonNumber("id").longValue(), admin.token()).json();

        assertEquals("Alta", tarefa.getJsonObject("dadosExtras").getString("Prioridade"));
        assertEquals("Web", tarefa.getJsonObject("dadosExtras").getString("Área"));
        assertEquals(404, Api.get("/salas/" + sala.id() + "/tarefas/999999", admin.token()).status());
    }

    @Test
    void minhasSalasTrazOsMembrosEOnlineMostraQuemEstaConectado() throws Exception {
        Usuario admin = Cenario.usuario("ADMIN");
        Usuario ana = Cenario.usuario("JOGADOR");
        Sala sala = Cenario.sala(admin);
        Cenario.entrar(sala, ana);

        JsonObject minha = Api.get("/salas/minhas", admin.token()).lista().getJsonObject(0);
        assertEquals(sala.id(), minha.getJsonNumber("id").longValue());
        assertEquals(List.of(ana.nome()), minha.getJsonArray("membros").getValuesAs(jakarta.json.JsonString::getString));

        try (SalaWs ws = SalaWs.conectar(sala.id(), ana.token())) {
            ws.aguardar("PRESENCA");
            assertEquals(List.of(ana.nome()), Api.get("/salas/" + sala.id() + "/online", admin.token()).lista()
                    .getValuesAs(jakarta.json.JsonString::getString));
        }
        assertEquals(403, Api.get("/salas/minhas", ana.token()).status());
    }

    @Test
    void rodadaAnteriorSoParaQuemAcompanhaETempoDeVoto() {
        Usuario admin = Cenario.usuario("ADMIN");
        Usuario ana = Cenario.usuario("JOGADOR");
        Sala sala = Cenario.sala(admin);
        Cenario.entrar(sala, ana);
        long tarefa = Cenario.tarefa(sala, admin, "PP-1");
        Api.post("/salas/" + sala.id() + "/tarefas/" + tarefa + "/liberar", admin.token(), null);
        String votacao = Cenario.votacao(sala, tarefa);
        Api.post(votacao + "/pontos", ana.token(), "{\"pontos\":13}");
        Api.post(votacao + "/nova-rodada", admin.token(), null);

        JsonObject doModerador = Api.get(votacao, admin.token()).json();
        JsonObject anterior = doModerador.getJsonArray("votosRodadaAnterior").getJsonObject(0);
        assertEquals(13, anterior.getInt("pontos"), "a rodada que acabou aparece inteira para o moderador");
        assertEquals(1, anterior.getInt("rodada"));
        assertTrue(anterior.containsKey("segundosPontos"));
        assertTrue(Api.get(votacao, ana.token()).json().getJsonArray("votosRodadaAnterior").isEmpty(),
                "o jogador não vê, para não ancorar o voto");
    }

    @Test
    void finalizarSemPontosFinaisComoNoOriginal() {
        Usuario admin = Cenario.usuario("ADMIN");
        Sala sala = Cenario.sala(admin);
        long tarefa = Cenario.tarefa(sala, admin, "PP-1");
        Api.post("/salas/" + sala.id() + "/tarefas/" + tarefa + "/liberar", admin.token(), null);

        Api.Resposta resposta = Api.post("/salas/" + sala.id() + "/tarefas/" + tarefa + "/finalizar", admin.token(), "{\"horasFinais\":4}");

        assertEquals(200, resposta.status(), resposta::toString);
        assertFalse(resposta.json().containsKey("pontosFinais"));
    }

    @Test
    void salaInativaQuandoOModeradorSaiEReativaNaListaDele() throws Exception {
        Usuario admin = Cenario.usuario("ADMIN");
        Usuario ana = Cenario.usuario("JOGADOR");
        Sala sala = Cenario.sala(admin);
        Cenario.entrar(sala, ana);
        Cenario.tarefa(sala, admin, "PP-1");

        try (SalaWs wsAna = SalaWs.conectar(sala.id(), ana.token())) {
            wsAna.aguardar("PRESENCA");
            SalaWs moderador = SalaWs.conectar(sala.id(), admin.token());
            moderador.aguardar("PRESENCA");
            moderador.close();

            wsAna.aguardar("SALA_DESATIVADA");
        }
        assertFalse(ativa(admin, sala), "inativada depois da espera, já que o moderador não voltou");

        // Como o buscarSalasComReativacao do original: listar as salas reativa as que têm tarefa pendente
        assertTrue(Api.get("/salas/minhas", admin.token()).lista().getJsonObject(0).getBoolean("ativa"));
        assertTrue(ativa(admin, sala));
    }

    @Test
    void moderadorQueVoltaAntesDaEsperaNaoInativaASala() throws Exception {
        Usuario admin = Cenario.usuario("ADMIN");
        Sala sala = Cenario.sala(admin);

        SalaWs primeira = SalaWs.conectar(sala.id(), admin.token());
        primeira.aguardar("PRESENCA");
        primeira.close();
        try (SalaWs refresh = SalaWs.conectar(sala.id(), admin.token())) {
            refresh.aguardar("PRESENCA");
            Thread.sleep(3500);
            assertTrue(ativa(admin, sala), "voltou dentro da espera (um refresh de página)");
        }
    }

    /** O caso visto em produção: servidor reiniciado com o moderador na tela da sala, que reconecta depois da espera. */
    @Test
    void moderadorQueReconectaNaSalaInativaAReativa() throws Exception {
        Usuario admin = Cenario.usuario("ADMIN");
        Usuario ana = Cenario.usuario("JOGADOR");
        Sala sala = Cenario.sala(admin);
        Cenario.tarefa(sala, admin, "PP-1");

        SalaWs moderador = SalaWs.conectar(sala.id(), admin.token());
        moderador.aguardar("PRESENCA");
        moderador.close();
        Thread.sleep(3500);
        assertFalse(ativa(admin, sala), "inativada: o moderador não voltou dentro da espera");

        // Jogador continua de fora enquanto a sala está inativa
        try (SalaWs wsAna = SalaWs.conectar(sala.id(), ana.token())) {
            assertEquals(1008, wsAna.codigoDeFechamento());
        }
        // O moderador volta pela tela da sala (sem passar por "Minhas salas"): a sala reativa e o jogador entra
        try (SalaWs volta = SalaWs.conectar(sala.id(), admin.token())) {
            volta.aguardar("PRESENCA");
            assertTrue(ativa(admin, sala));
            assertEquals(200, Api.post("/salas/codigo/" + sala.codigo() + "/entrar", ana.token(), null).status());
        }
    }

    private static boolean ativa(Usuario admin, Sala sala) {
        return Api.get("/salas/codigo/" + sala.codigo(), admin.token()).json().getBoolean("ativa");
    }
}
