package com.planningapp.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Estatísticas agregadas das votações. Os totais usam os valores finais definidos pelo
 * moderador; em tarefas sem valor final, cai para a média dos votos da rodada final
 * (rodadaAtual), ignorando a carta café (pontos = 0).
 */
public record EstatisticasDTO(
        Resumo resumo,
        List<PorSprint> porSprint,
        List<PorUsuario> porUsuario,
        Map<Integer, Long> distribuicaoPontos,
        Map<Double, Long> distribuicaoHoras,
        List<TarefaEstatistica> tarefas,
        List<Opcao> salas,
        List<String> sprints
) {

    public record Resumo(
            int tarefasEstimadas,
            int tarefasPuladas,
            long totalVotos,
            int participantes,
            Double totalPontos,
            Double mediaPontos,
            Double totalHoras,
            Double mediaHoras,
            Double mediaRodadas,
            Double taxaConsenso,
            Double duracaoMediaMinutos
    ) {}

    public record PorSprint(
            String sprint,
            int tarefas,
            int puladas,
            Double totalPontos,
            Double mediaPontos,
            Double totalHoras,
            Double mediaHoras,
            Double mediaRodadas,
            Double taxaConsenso
    ) {}

    public record PorUsuario(
            String usuario,
            String perfil,
            int tarefas,
            long votosPontos,
            long cafes,
            Double mediaPontos,
            long votosHoras,
            Double mediaHoras,
            long votosHorasTeste,
            Double mediaHorasTeste,
            Double desvioMedioPontos,
            Double taxaAcertoModa
    ) {}

    public record TarefaEstatistica(
            Long id,
            Long numero,
            String titulo,
            String sprint,
            String sala,
            int votantes,
            Integer modaPontos,
            Double mediaPontos,
            Integer minPontos,
            Integer maxPontos,
            Double mediaHoras,
            Double mediaHorasTeste,
            /** Valores definidos pelo moderador ao finalizar; null em tarefas antigas. */
            Integer pontosFinais,
            Double horasFinais,
            Double horasTesteFinais,
            int rodadas,
            boolean consenso,
            Instant estimadaEm,
            Double duracaoMinutos
    ) {}

    public record Opcao(Long id, String nome) {}
}
