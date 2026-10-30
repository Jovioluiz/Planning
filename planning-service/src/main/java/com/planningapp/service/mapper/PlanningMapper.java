package com.planningapp.service.mapper;

import com.planningapp.dto.SalaDTO;
import com.planningapp.dto.TarefaDTO;
import com.planningapp.dto.UsuarioDTO;
import com.planningapp.model.Sala;
import com.planningapp.model.Tarefa;
import com.planningapp.model.Usuario;

/** Conversão entidade -> DTO. As entidades nunca saem do módulo de service. */
public final class PlanningMapper {

    private PlanningMapper() {
    }

    public static SalaDTO toDTO(Sala sala, long quantidadeTarefas) {
        return new SalaDTO(
                sala.getId(),
                sala.getNome(),
                sala.getCodigo(),
                sala.isAtiva(),
                sala.getCriadaEm(),
                sala.getModerador() != null ? sala.getModerador().getUsuario() : null,
                (int) quantidadeTarefas);
    }

    public static TarefaDTO toDTO(Tarefa tarefa) {
        return new TarefaDTO(
                tarefa.getId(),
                tarefa.getNumero(),
                tarefa.getTitulo(),
                tarefa.getDescricao(),
                tarefa.getSprint(),
                tarefa.getStatus().name(),
                tarefa.getFase() != null ? tarefa.getFase().name() : null,
                tarefa.getFaseHorasTeste() != null ? tarefa.getFaseHorasTeste().name() : null,
                tarefa.getRodadaAtual(),
                tarefa.getPontosFinais(),
                tarefa.getHorasFinais(),
                tarefa.getHorasTesteFinais(),
                tarefa.getSala() != null ? tarefa.getSala().getId() : null,
                tarefa.getLiberadaEm(),
                tarefa.getEncerradaEm(),
                tarefa.getHorasLiberadasEm(),
                DadosExtras.deJson(tarefa.getDadosExtras()),
                tarefa.isDoRedmine());
    }

    /** Nunca expõe o hash da senha. */
    public static UsuarioDTO toDTO(Usuario usuario) {
        return new UsuarioDTO(usuario.getId(), usuario.getUsuario(), usuario.getPerfil().name());
    }
}
