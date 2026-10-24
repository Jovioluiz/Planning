package com.planningapp.service.votacao;

import com.planningapp.dto.ResumoHorasDTO;
import com.planningapp.dto.ResumoPontosDTO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumoVotacaoTest {

    @Test
    void cafeFicaForaDaMediaMasContaNosVotos() {
        ResumoPontosDTO resumo = ResumoVotacao.resumirPontos(List.of(3, 5, 0, 8));

        assertEquals(4, resumo.votos());
        assertEquals(1, resumo.cafes());
        assertEquals(5.3, resumo.media());
        assertEquals(3, resumo.minimo());
        assertEquals(8, resumo.maximo());
        assertEquals(5, resumo.sugestao());
        assertFalse(resumo.consenso());
    }

    @Test
    void consensoQuandoTodosVotamNaMesmaCartaIgnorandoCafe() {
        ResumoPontosDTO resumo = ResumoVotacao.resumirPontos(List.of(5, 5, 0));

        assertTrue(resumo.consenso());
        assertEquals(5, resumo.sugestao());
    }

    @Test
    void soCafeNaoTemMediaNemSugestao() {
        ResumoPontosDTO resumo = ResumoVotacao.resumirPontos(List.of(0, 0));

        assertEquals(2, resumo.cafes());
        assertNull(resumo.media());
        assertNull(resumo.sugestao());
        assertFalse(resumo.consenso());
    }

    @Test
    void ignoraQuemAindaNaoVotou() {
        ResumoPontosDTO resumo = ResumoVotacao.resumirPontos(Arrays.asList(2, null, 3));

        assertEquals(2, resumo.votos());
        assertEquals(2.5, resumo.media());
    }

    @Test
    void resumeHoras() {
        ResumoHorasDTO resumo = ResumoVotacao.resumirHoras(Arrays.asList(4, 8, null, 6));

        assertEquals(3, resumo.votos());
        assertEquals(6.0, resumo.media());
        assertEquals(4, resumo.minimo());
        assertEquals(8, resumo.maximo());
    }

    @Test
    void semVotosDeHoras() {
        ResumoHorasDTO resumo = ResumoVotacao.resumirHoras(Arrays.asList(null, null));

        assertEquals(0, resumo.votos());
        assertNull(resumo.media());
    }
}
