package com.planningapp.service.estatisticas;

import com.planningapp.dto.EstatisticasDTO;
import com.planningapp.dto.EstatisticasDTO.PorUsuario;
import com.planningapp.service.estatisticas.CalculadoraEstatisticas.Filtro;
import com.planningapp.service.estatisticas.CalculadoraEstatisticas.TarefaEncerrada;
import com.planningapp.service.estatisticas.CalculadoraEstatisticas.Voto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraEstatisticasTest {

    private static final Instant DIA_1 = Instant.parse("2026-10-01T13:00:00Z");
    private static final Instant DIA_2 = Instant.parse("2026-10-02T13:00:00Z");

    /** Consenso em 5, com café e um testador; o moderador fechou em 5 pontos e 8 horas. */
    private final TarefaEncerrada login = new TarefaEncerrada(1L, "PP-1", "Login", "S1", 1L, "Sprint 1", false, 1,
            5, 8, null, DIA_1.minusSeconds(30 * 60), DIA_1, List.of(
            new Voto("ana", "JOGADOR", 5, 8, null),
            new Voto("bia", "JOGADOR", 5, 6, null),
            new Voto("caio", "JOGADOR", 0, null, null),
            new Voto("tess", "TESTE", null, null, 4)));

    /** Sem valores finais (cai na média dos votos) e sem sprint. */
    private final TarefaEncerrada cadastro = new TarefaEncerrada(2L, "PP-2", "Cadastro", null, 1L, "Sprint 1", false, 2,
            null, null, null, null, DIA_2, List.of(
            new Voto("ana", "JOGADOR", 3, null, null),
            new Voto("bia", "JOGADOR", 8, null, null)));

    private final TarefaEncerrada pulada = new TarefaEncerrada(3L, "PP-3", "Pulada", "S1", 1L, "Sprint 1", true, 1,
            null, null, null, null, DIA_1, List.of());

    private final TarefaEncerrada outraSala = new TarefaEncerrada(4L, "PP-9", "Relatório", "S2", 2L, "Outra", false, 1,
            13, null, null, null, DIA_1, List.of(new Voto("ana", "JOGADOR", 13, null, null)));

    private final List<TarefaEncerrada> todas = List.of(login, cadastro, pulada, outraSala);

    @Test
    void resumoUsaValoresFinaisOuAMediaDosVotos() {
        EstatisticasDTO.Resumo resumo = CalculadoraEstatisticas.calcular(todas, Filtro.nenhum()).resumo();

        assertEquals(3, resumo.tarefasEstimadas());
        assertEquals(1, resumo.tarefasPuladas());
        assertEquals(6, resumo.totalVotos(), "votos de pontos, café incluído");
        assertEquals(4, resumo.participantes());
        assertEquals(23.5, resumo.totalPontos(), "5 (final) + 5.5 (média) + 13 (final)");
        assertEquals(7.83, resumo.mediaPontos());
        assertEquals(4.0, resumo.totalHorasTeste());
        assertEquals(66.67, resumo.taxaConsenso());
        assertEquals(1.33, resumo.mediaRodadas());
        assertEquals(30.0, resumo.duracaoMediaMinutos());
    }

    @Test
    void tarefaIgnoraOCafeNaMediaEUsaAMaiorCartaNoEmpateDaModa() {
        List<EstatisticasDTO.TarefaEstatistica> tarefas = CalculadoraEstatisticas.calcular(todas, Filtro.nenhum()).tarefas();

        EstatisticasDTO.TarefaEstatistica t1 = tarefas.stream().filter(t -> t.id() == 1L).findFirst().orElseThrow();
        assertEquals(3, t1.votantes());
        assertEquals(5.0, t1.mediaPontos());
        assertEquals(7.0, t1.mediaHoras());
        assertTrue(t1.consenso());

        EstatisticasDTO.TarefaEstatistica t2 = tarefas.get(0);
        assertEquals("PP-2", t2.numero(), "mais recente primeiro");
        assertEquals(8, t2.modaPontos());
        assertFalse(t2.consenso());
        assertEquals("Sem sprint", t2.sprint());
    }

    @Test
    void agrupaPorSprintContandoAsPuladas() {
        List<EstatisticasDTO.PorSprint> porSprint = CalculadoraEstatisticas.calcular(todas, Filtro.nenhum()).porSprint();

        assertEquals(List.of("S1", "S2", "Sem sprint"), porSprint.stream().map(EstatisticasDTO.PorSprint::sprint).toList());
        assertEquals(1, porSprint.get(0).tarefas());
        assertEquals(1, porSprint.get(0).puladas());
    }

    @Test
    void porUsuarioMedeDesvioEAcerto() {
        List<PorUsuario> porUsuario = CalculadoraEstatisticas.calcular(todas, Filtro.nenhum()).porUsuario();

        PorUsuario ana = porUsuario.get(0);
        assertEquals("ana", ana.usuario());
        assertEquals(3, ana.tarefas());
        assertEquals(-0.83, ana.desvioMedioPontos(), "(0 - 2.5 + 0) / 3");
        assertEquals(66.67, ana.taxaAcertoModa(), "acertou PP-1 e PP-9; na PP-2 a referência é a moda (8)");

        PorUsuario caio = porUsuario.stream().filter(u -> u.usuario().equals("caio")).findFirst().orElseThrow();
        assertEquals(1, caio.cafes());
        assertEquals(0, caio.votosPontos());
        assertNull(caio.taxaAcertoModa());

        PorUsuario tess = porUsuario.stream().filter(u -> u.usuario().equals("tess")).findFirst().orElseThrow();
        assertEquals("TESTE", tess.perfil());
        assertEquals(4.0, tess.mediaHorasTeste());
    }

    @Test
    void distribuicaoFicaEmOrdemNumerica() {
        EstatisticasDTO dto = CalculadoraEstatisticas.calcular(todas, Filtro.nenhum());

        assertEquals(List.of("0", "3", "5", "8", "13"), List.copyOf(dto.distribuicaoPontos().keySet()));
        assertEquals(2L, dto.distribuicaoPontos().get("5"));
        assertEquals(List.of("6", "8"), List.copyOf(dto.distribuicaoHoras().keySet()));
    }

    @Test
    void filtraPorSalaSprintEPeriodo() {
        EstatisticasDTO daOutraSala = CalculadoraEstatisticas.calcular(todas, new Filtro(2L, null, null, null));
        assertEquals(1, daOutraSala.resumo().tarefasEstimadas());
        assertEquals(List.of("S2"), daOutraSala.sprints());
        assertEquals(2, daOutraSala.salas().size(), "as opções de sala não dependem do filtro");

        EstatisticasDTO semSprint = CalculadoraEstatisticas.calcular(todas, new Filtro(null, "Sem sprint", null, null));
        assertEquals(List.of("PP-2"), semSprint.tarefas().stream().map(EstatisticasDTO.TarefaEstatistica::numero).toList());

        EstatisticasDTO doDia2 = CalculadoraEstatisticas.calcular(todas,
                new Filtro(null, null, Instant.parse("2026-10-02T03:00:00Z"), Instant.parse("2026-10-03T03:00:00Z")));
        assertEquals(List.of("PP-2"), doDia2.tarefas().stream().map(EstatisticasDTO.TarefaEstatistica::numero).toList());
        assertEquals(0, doDia2.resumo().tarefasPuladas());
    }

    @Test
    void semTarefasNaoQuebra() {
        EstatisticasDTO vazio = CalculadoraEstatisticas.calcular(List.of(), Filtro.nenhum());

        assertEquals(0, vazio.resumo().tarefasEstimadas());
        assertNull(vazio.resumo().mediaPontos());
        assertTrue(vazio.distribuicaoPontos().isEmpty());
    }
}
