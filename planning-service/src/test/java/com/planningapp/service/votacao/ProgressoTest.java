package com.planningapp.service.votacao;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressoTest {

    @Test
    void aguardaQuemEstaOnlineEAindaNaoVotou() {
        Progresso progresso = Progresso.calcular(Set.of("ana", "bia", "caio"), Set.of("bia"));

        assertEquals(List.of("ana", "caio"), progresso.aguardando());
        assertFalse(progresso.todosVotaram());
    }

    @Test
    void todosVotaramQuandoNinguemOnlineEstaPendente() {
        assertTrue(Progresso.calcular(Set.of("ana", "bia"), Set.of("ana", "bia")).todosVotaram());
    }

    @Test
    void quemVotouESaiuContinuaContando() {
        assertTrue(Progresso.calcular(Set.of("ana"), Set.of("ana", "bia")).todosVotaram());
    }

    @Test
    void semNenhumVotoNaoEstaCompleto() {
        assertFalse(Progresso.calcular(Set.of(), Set.of()).todosVotaram());
    }
}
