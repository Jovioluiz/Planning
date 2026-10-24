package com.planningapp.web.websocket;

import com.planningapp.dto.EventoSala;
import com.planningapp.dto.LoteEventosSala;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;

/**
 * Liga os eventos de negócio (disparados pelos EJBs do service) ao WebSocket. O service não sabe que existe
 * WebSocket nem JMS: ele só dispara {@link EventoSala}, e a camada web decide como entregar.
 *
 * <pre>
 *   EJB ──CDI AFTER_SUCCESS──► SalaNotificador ──JMS──► tópico ──► EntregaEventosSala (1 por nó) ──► WebSocket
 * </pre>
 */
@ApplicationScoped
public class SalaNotificador {

    @Inject
    private PublicadorEventosSala publicador;

    @Inject
    private Jsonb jsonb;

    /** {@code AFTER_SUCCESS}: só publica se a transação do EJB fizer commit. */
    void aoEventoDaSala(@Observes(during = TransactionPhase.AFTER_SUCCESS) EventoSala evento) {
        publicar(evento);
    }

    /** Um evento por mensagem, na ordem do lote (o mesmo produtor garante a ordem no tópico). */
    void aoLoteDaSala(@Observes(during = TransactionPhase.AFTER_SUCCESS) LoteEventosSala lote) {
        lote.eventos().forEach(this::publicar);
    }

    private void publicar(EventoSala evento) {
        publicador.publicar(evento.salaId(), jsonb.toJson(evento));
    }
}
