package com.planningapp.migracao;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstadoTarefaTest {

    @Test
    void puladaGanhaDeEstimadaComoNoOriginal() {
        assertEquals(new EstadoTarefa("PULADA", null, null), EstadoTarefa.de(false, true, true, true, true, true, true, true));
    }

    @Test
    void liberadaSegueAsFlagsDaFaseMaisAvancada() {
        assertEquals("PONTOS", EstadoTarefa.de(true, false, false, false, false, false, false, false).fase());
        assertEquals("PONTOS_REVELADOS", EstadoTarefa.de(true, false, false, true, false, false, false, false).fase());
        assertEquals("HORAS", EstadoTarefa.de(true, false, false, true, true, false, false, false).fase());
        assertEquals("HORAS_REVELADAS", EstadoTarefa.de(true, false, false, true, true, true, false, false).fase());
    }

    @Test
    void trilhaDeTesteSoNaVotacaoAberta() {
        assertEquals("VOTACAO", EstadoTarefa.de(true, false, false, false, false, false, true, false).faseHorasTeste());
        assertEquals("REVELADA", EstadoTarefa.de(true, false, false, false, false, false, true, true).faseHorasTeste());
        assertEquals(new EstadoTarefa("FILA", null, null), EstadoTarefa.de(false, false, false, false, false, false, true, false));
    }
}
