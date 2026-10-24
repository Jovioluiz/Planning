package com.planningapp.it;

import com.planningapp.it.Cenario.Sala;
import com.planningapp.it.Cenario.Usuario;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Importação (JSON e CSV, com os dois @Consumes) e as estatísticas calculadas sobre o que foi votado. */
class ImportacaoEstatisticasIT {

    @Test
    void csvDoExcelImportaEIgnoraDuplicadas() {
        Usuario admin = Cenario.usuario("ADMIN");
        Sala sala = Cenario.sala(admin);
        String csv = "﻿Número;Título;Descrição;Sprint\r\n"
                + "PP-1;Login;\"Com SSO; e \"\"lembrar-me\"\"\";S1\r\n"
                + "PP-2;Relatório;;S1\r\n"
                + "PP-1;Repetida;;S1\r\n";

        JsonObject resultado = Api.postCsv("/salas/" + sala.id() + "/tarefas/importar", admin.token(), csv).json();

        assertEquals(2, resultado.getJsonArray("importadas").size());
        assertEquals(List.of("PP-1"), resultado.getJsonArray("ignoradas").getValuesAs(jakarta.json.JsonString::getString));
        JsonObject login = tarefa(sala, admin, "PP-1");
        assertEquals("Com SSO; e \"lembrar-me\"", login.getString("descricao"));
        assertEquals("S1", login.getString("sprint"));
    }

    @Test
    void linhaInvalidaNaoImportaNadaEDizOndeEsta() {
        Usuario admin = Cenario.usuario("ADMIN");
        Sala sala = Cenario.sala(admin);

        Api.Resposta resposta = Api.post("/salas/" + sala.id() + "/tarefas/importar", admin.token(),
                "[{\"numero\":\"PP-1\",\"titulo\":\"Ok\"},{\"numero\":\"PP-2\"}]");

        assertEquals(400, resposta.status());
        assertEquals(List.of("Linha 2: o título é obrigatório"),
                resposta.json().getJsonArray("detalhes").getValuesAs(jakarta.json.JsonString::getString));
        assertEquals(0, Api.get("/salas/" + sala.id() + "/tarefas", admin.token()).lista().size());
    }

    @Test
    void estatisticasContamSoAsTarefasDoModeradorEARodadaFinal() {
        Usuario admin = Cenario.usuario("ADMIN");
        Usuario ana = Cenario.usuario("JOGADOR");
        Usuario bia = Cenario.usuario("JOGADOR");
        Sala sala = Cenario.sala(admin);
        Cenario.entrar(sala, ana, bia);
        Api.post("/salas/" + sala.id() + "/tarefas/importar", admin.token(),
                "[{\"numero\":\"PP-1\",\"titulo\":\"A\",\"sprint\":\"S1\"},{\"numero\":\"PP-2\",\"titulo\":\"B\",\"sprint\":\"S1\"}]");
        long pp1 = tarefa(sala, admin, "PP-1").getJsonNumber("id").longValue();
        long pp2 = tarefa(sala, admin, "PP-2").getJsonNumber("id").longValue();
        String tarefas = "/salas/" + sala.id() + "/tarefas/";

        // PP-1: rodada 1 com 3 e 13 (não conta), rodada 2 com 5 e 5, finalizada em 5
        Api.post(tarefas + pp1 + "/liberar", admin.token(), null);
        Api.post(Cenario.votacao(sala, pp1) + "/pontos", ana.token(), "{\"pontos\":3}");
        Api.post(Cenario.votacao(sala, pp1) + "/pontos", bia.token(), "{\"pontos\":13}");
        Api.post(Cenario.votacao(sala, pp1) + "/nova-rodada", admin.token(), null);
        Api.post(Cenario.votacao(sala, pp1) + "/pontos", ana.token(), "{\"pontos\":5}");
        Api.post(Cenario.votacao(sala, pp1) + "/pontos", bia.token(), "{\"pontos\":5}");
        assertEquals(200, Api.post(tarefas + pp1 + "/finalizar", admin.token(), "{\"pontosFinais\":5}").status());
        // PP-2: pulada
        Api.post(tarefas + pp2 + "/liberar", admin.token(), null);
        assertEquals(200, Api.post(tarefas + pp2 + "/pular", admin.token(), null).status());

        JsonObject estatisticas = Api.get("/estatisticas", admin.token()).json();
        JsonObject resumo = estatisticas.getJsonObject("resumo");
        assertEquals(1, resumo.getInt("tarefasEstimadas"));
        assertEquals(1, resumo.getInt("tarefasPuladas"));
        assertEquals(2, resumo.getInt("totalVotos"), "só a rodada final");
        assertEquals(100.0, resumo.getJsonNumber("taxaConsenso").doubleValue());
        assertEquals(2, estatisticas.getJsonObject("distribuicaoPontos").getInt("5"));
        assertEquals(1, estatisticas.getJsonArray("salas").size(), "o ADMIN só vê as próprias salas");

        assertEquals(403, Api.get("/estatisticas", ana.token()).status());
    }

    private static JsonObject tarefa(Sala sala, Usuario admin, String numero) {
        return Api.get("/salas/" + sala.id() + "/tarefas", admin.token()).lista().stream()
                .map(JsonValue::asJsonObject)
                .filter(t -> t.getString("numero").equals(numero))
                .findFirst()
                .orElseThrow();
    }
}
