package com.planningapp.dto;

import java.time.Instant;
import java.util.List;

/**
 * Evento de uma sala. É disparado como evento CDI pelo service e enviado em JSON pelo WebSocket.
 * Campos que não se aplicam ao tipo ficam nulos (e o JSON-B os omite).
 */
public record EventoSala(
        TipoEventoSala tipo,
        Long salaId,
        TarefaDTO tarefa,
        String usuario,
        List<String> online,
        Instant em) {

    public static EventoSala deTarefa(TipoEventoSala tipo, TarefaDTO tarefa) {
        return new EventoSala(tipo, tarefa.salaId(), tarefa, null, null, Instant.now());
    }

    /** Avisa que {@code usuario} votou ({@code VOTO_REGISTRADO} ou {@code VOTO_TESTE_REGISTRADO}), sem dizer em quê. */
    public static EventoSala voto(TipoEventoSala tipo, TarefaDTO tarefa, String usuario) {
        return new EventoSala(tipo, tarefa.salaId(), tarefa, usuario, null, Instant.now());
    }

    /** {@code PARTICIPANTE_REMOVIDO} ou {@code PARTICIPANTE_ADICIONADO}. */
    public static EventoSala participante(TipoEventoSala tipo, TarefaDTO tarefa, String usuario) {
        return new EventoSala(tipo, tarefa.salaId(), tarefa, usuario, null, Instant.now());
    }

    public static EventoSala salaDesativada(Long salaId) {
        return new EventoSala(TipoEventoSala.SALA_DESATIVADA, salaId, null, null, null, Instant.now());
    }

    public static EventoSala tarefasImportadas(Long salaId) {
        return new EventoSala(TipoEventoSala.TAREFAS_IMPORTADAS, salaId, null, null, null, Instant.now());
    }

    public static EventoSala presenca(Long salaId, String usuario, List<String> online) {
        return new EventoSala(TipoEventoSala.PRESENCA, salaId, null, usuario, online, Instant.now());
    }
}
