package com.planningapp.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Estatísticas das tarefas encerradas. Mesmos nomes de campos do Planning original, para a página
 * /estatisticas do Angular servir com poucas mudanças.
 *
 * <p>Os totais usam os valores finais do moderador; sem eles, a média dos votos da rodada final. A carta
 * café (0) fica fora das médias. Os campos de horas de teste não existiam no original.</p>
 *
 * @param distribuicaoPontos votos por carta, em ordem ("0" é o café)
 * @param distribuicaoHoras  votos por quantidade de horas, em ordem
 * @param salas              salas que aparecem no escopo de quem consulta (para o filtro)
 * @param sprints            sprints da sala filtrada (para o filtro)
 */
public record EstatisticasDTO(
        Resumo resumo,
        List<PorSprint> porSprint,
        List<PorUsuario> porUsuario,
        Map<String, Long> distribuicaoPontos,
        Map<String, Long> distribuicaoHoras,
        List<TarefaEstatistica> tarefas,
        List<Opcao> salas,
        List<String> sprints) {

    /**
     * @param taxaConsenso        % das tarefas com votos em que todos escolheram a mesma carta
     * @param duracaoMediaMinutos da liberação ao encerramento
     */
    public record Resumo(
            int tarefasEstimadas,
            int tarefasPuladas,
            long totalVotos,
            int participantes,
            Double totalPontos,
            Double mediaPontos,
            Double totalHoras,
            Double mediaHoras,
            Double totalHorasTeste,
            Double mediaHorasTeste,
            Double mediaRodadas,
            Double taxaConsenso,
            Double duracaoMediaMinutos) {
    }

    public record PorSprint(
            String sprint,
            int tarefas,
            int puladas,
            Double totalPontos,
            Double mediaPontos,
            Double totalHoras,
            Double mediaHoras,
            Double totalHorasTeste,
            Double mediaRodadas,
            Double taxaConsenso) {
    }

    /**
     * @param desvioMedioPontos quanto o usuário estima, em média, acima (+) ou abaixo (-) dos pontos que valeram
     * @param taxaAcertoModa    % dos votos iguais à carta final (ou à mais votada, se não houver final)
     */
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
            Double taxaAcertoModa) {
    }

    /** @param estimadaEm quando foi finalizada (mesmo nome do original) */
    public record TarefaEstatistica(
            Long id,
            String numero,
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
            Integer pontosFinais,
            Integer horasFinais,
            Integer horasTesteFinais,
            int rodadas,
            boolean consenso,
            Instant estimadaEm,
            Double duracaoMinutos) {
    }

    public record Opcao(Long id, String nome) {
    }
}
