package com.planningapp.dto;

import java.util.List;

/**
 * Eventos de uma mesma transação que precisam chegar na ordem (ex.: {@code VOTO_REGISTRADO} e em seguida
 * {@code TODOS_VOTARAM}). Disparar dois {@link EventoSala} separados não garante isso: cada observer
 * {@code AFTER_SUCCESS} vira uma sincronização da transação, e o gerenciador de transações não promete
 * ordem entre elas (o Narayana chega a executá-las ao contrário).
 */
public record LoteEventosSala(List<EventoSala> eventos) {
}
