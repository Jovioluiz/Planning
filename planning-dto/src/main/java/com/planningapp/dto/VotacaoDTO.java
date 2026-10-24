package com.planningapp.dto;

import java.util.List;

/**
 * Situação da votação da rodada atual. Os resumos só vêm depois que a respectiva fase é revelada.
 *
 * @param votos                  votos de pontos/horas dos jogadores
 * @param aguardando             jogadores online que ainda não votaram na fase aberta (PONTOS ou HORAS);
 *                               nulo quando nenhuma fase está aberta para voto
 * @param todosVotaram           todos os jogadores online já votaram na fase aberta; nulo como acima
 * @param faseHorasTeste         trilha de horas de teste (VOTACAO, REVELADA); nula enquanto não liberada
 * @param votosTeste             votos de horas de teste dos testadores
 * @param aguardandoTeste        testadores online que ainda não votaram; nulo fora da fase VOTACAO
 * @param todosTestadoresVotaram todos os testadores online já votaram; nulo fora da fase VOTACAO
 * @param votosRodadaAnterior    votos da rodada anterior, como referência (o front mostra "Rodada N-1"); só para o
 *                               moderador, OBSERVADOR e TESTE, como no original. Vazio na primeira rodada
 */
public record VotacaoDTO(
        Long tarefaId,
        String status,
        String fase,
        int rodada,
        List<VotoDTO> votos,
        ResumoPontosDTO resumoPontos,
        ResumoHorasDTO resumoHoras,
        List<String> aguardando,
        Boolean todosVotaram,
        String faseHorasTeste,
        List<VotoTesteDTO> votosTeste,
        ResumoHorasDTO resumoHorasTeste,
        List<String> aguardandoTeste,
        Boolean todosTestadoresVotaram,
        List<VotoDTO> votosRodadaAnterior) {
}
