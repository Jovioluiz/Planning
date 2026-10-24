package com.planningapp.service.mapper;

import com.planningapp.dto.SalaDTO;
import com.planningapp.dto.TarefaDTO;
import com.planningapp.dto.UsuarioDTO;
import com.planningapp.model.FaseHorasTeste;
import com.planningapp.model.FaseVotacao;
import com.planningapp.model.Perfil;
import com.planningapp.model.Sala;
import com.planningapp.model.Tarefa;
import com.planningapp.model.Usuario;
import com.planningapp.security.Papeis;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlanningMapperTest {

    private final Usuario admin = new Usuario("jovio", "hash-qualquer", Perfil.ADMIN);

    @Test
    void converteSalaComModeradorEQuantidadeDeTarefas() {
        SalaDTO dto = PlanningMapper.toDTO(new Sala("Sprint 42", admin), 3);

        assertEquals("Sprint 42", dto.nome());
        assertEquals("jovio", dto.moderador());
        assertEquals(3, dto.quantidadeTarefas());
    }

    @Test
    void converteTarefaNovaComStatusFila() {
        Sala sala = new Sala("Sprint 42", admin);
        Tarefa tarefa = new Tarefa("PP-1", "Tela de login", null);
        sala.adicionarTarefa(tarefa);

        TarefaDTO dto = PlanningMapper.toDTO(tarefa);

        assertEquals("PP-1", dto.numero());
        assertEquals("FILA", dto.status());
        assertNull(dto.pontosFinais());
    }

    @Test
    void novaRodadaVoltaParaPontosEFechaATrilhaDeTeste() {
        Sala sala = new Sala("Sprint 42", admin);
        Tarefa tarefa = new Tarefa("PP-1", "Tela de login", null);
        sala.adicionarTarefa(tarefa);
        tarefa.liberar();
        tarefa.avancarPara(FaseVotacao.HORAS_REVELADAS);
        tarefa.avancarHorasTestePara(FaseHorasTeste.REVELADA);

        tarefa.novaRodada();
        TarefaDTO dto = PlanningMapper.toDTO(tarefa);

        assertEquals("PONTOS", dto.fase());
        assertNull(dto.faseHorasTeste());
        assertEquals(2, dto.rodadaAtual());
    }

    @Test
    void finalizarGuardaOsValoresFinaisESaiDaVotacao() {
        Sala sala = new Sala("Sprint 42", admin);
        Tarefa tarefa = new Tarefa("PP-1", "Tela de login", null);
        sala.adicionarTarefa(tarefa);
        tarefa.liberar();

        tarefa.finalizar(5, 6, 3);
        TarefaDTO dto = PlanningMapper.toDTO(tarefa);

        assertEquals("ESTIMADA", dto.status());
        assertNull(dto.fase());
        assertEquals(5, dto.pontosFinais());
        assertEquals(6, dto.horasFinais());
        assertEquals(3, dto.horasTesteFinais());
    }

    @Test
    void pularEncerraSemEstimativaELiberarDeNovoAbreRodadaNova() {
        Sala sala = new Sala("Sprint 42", admin);
        Tarefa tarefa = new Tarefa("PP-1", "Tela de login", null);
        sala.adicionarTarefa(tarefa);
        tarefa.liberar();
        tarefa.avancarPara(FaseVotacao.HORAS);
        tarefa.avancarHorasTestePara(FaseHorasTeste.VOTACAO);

        tarefa.pular();
        TarefaDTO pulada = PlanningMapper.toDTO(tarefa);

        assertEquals("PULADA", pulada.status());
        assertNull(pulada.fase());
        assertNull(pulada.faseHorasTeste());
        assertNull(pulada.pontosFinais());

        tarefa.liberar();
        TarefaDTO deNovo = PlanningMapper.toDTO(tarefa);

        assertEquals("LIBERADA", deNovo.status());
        assertEquals("PONTOS", deNovo.fase());
        assertEquals(2, deNovo.rodadaAtual());
    }

    @Test
    void converteUsuarioSemExporOHash() {
        UsuarioDTO dto = PlanningMapper.toDTO(admin);

        assertEquals("jovio", dto.usuario());
        assertEquals("ADMIN", dto.perfil());
    }

    /** Garante que as constantes de {@code @RolesAllowed} batem com o enum gravado no banco. */
    @Test
    void papeisCorrespondemAosPerfis() {
        Set<String> perfis = Arrays.stream(Perfil.values()).map(Enum::name).collect(Collectors.toSet());

        assertEquals(perfis, Papeis.TODOS);
    }
}
