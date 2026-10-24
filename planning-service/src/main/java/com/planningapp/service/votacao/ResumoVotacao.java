package com.planningapp.service.votacao;

import com.planningapp.dto.ResumoHorasDTO;
import com.planningapp.dto.ResumoPontosDTO;
import com.planningapp.model.Estimativa;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Objects;

/** Cálculos do resumo de uma rodada. Funções puras, sem banco: dá para testar sem container. */
public final class ResumoVotacao {

    private ResumoVotacao() {
    }

    /** @param pontos votos de pontos da rodada (nulos são ignorados; 0 é café e fica fora das estatísticas) */
    public static ResumoPontosDTO resumirPontos(List<Integer> pontos) {
        List<Integer> votos = pontos.stream().filter(Objects::nonNull).toList();
        List<Integer> semCafe = votos.stream().filter(p -> p != Estimativa.CAFE).toList();
        int cafes = votos.size() - semCafe.size();

        if (semCafe.isEmpty()) {
            return new ResumoPontosDTO(votos.size(), cafes, null, null, null, null, false);
        }

        IntSummaryStatistics estatisticas = semCafe.stream().mapToInt(Integer::intValue).summaryStatistics();
        double media = arredondar(estatisticas.getAverage());
        boolean consenso = estatisticas.getMin() == estatisticas.getMax();
        return new ResumoPontosDTO(votos.size(), cafes, media, estatisticas.getMin(), estatisticas.getMax(),
                Cartas.cartaMaisProxima(media), consenso);
    }

    /** @param horas votos de horas da rodada (nulos são ignorados) */
    public static ResumoHorasDTO resumirHoras(List<Integer> horas) {
        List<Integer> votos = horas.stream().filter(Objects::nonNull).toList();
        if (votos.isEmpty()) {
            return new ResumoHorasDTO(0, null, null, null);
        }
        IntSummaryStatistics estatisticas = votos.stream().mapToInt(Integer::intValue).summaryStatistics();
        return new ResumoHorasDTO(votos.size(), arredondar(estatisticas.getAverage()),
                estatisticas.getMin(), estatisticas.getMax());
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 10) / 10.0;
    }
}
