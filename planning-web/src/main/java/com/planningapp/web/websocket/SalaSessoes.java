package com.planningapp.web.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Conexões WebSocket abertas, agrupadas por sala. Uma instância por aplicação. */
@ApplicationScoped
public class SalaSessoes {

    static final String PROP_USUARIO = "usuario";
    static final String PROP_SALA = "salaId";

    private static final Logger LOG = Logger.getLogger(SalaSessoes.class.getName());

    private final Map<Long, Set<Session>> porSala = new ConcurrentHashMap<>();

    public void adicionar(Long salaId, Session sessao) {
        porSala.computeIfAbsent(salaId, id -> ConcurrentHashMap.newKeySet()).add(sessao);
    }

    public void remover(Long salaId, Session sessao) {
        porSala.computeIfPresent(salaId, (id, sessoes) -> {
            sessoes.remove(sessao);
            return sessoes.isEmpty() ? null : sessoes;
        });
    }

    public void enviar(Long salaId, String mensagem) {
        for (Session sessao : porSala.getOrDefault(salaId, Set.of())) {
            enviar(sessao, mensagem);
        }
    }

    /**
     * Envio síncrono e sincronizado por sessão: o Undertow não aceita dois envios simultâneos na mesma
     * conexão ("The remote endpoint was in state [TEXT_FULL_WRITING]"), o que acontece quando dois eventos
     * da mesma sala chegam ao mesmo tempo.
     */
    public void enviar(Session sessao, String mensagem) {
        if (!sessao.isOpen()) {
            return;
        }
        synchronized (sessao) {
            try {
                sessao.getBasicRemote().sendText(mensagem);
            } catch (IOException | IllegalStateException e) {
                LOG.log(Level.FINE, "Falha ao enviar para a sessão " + sessao.getId(), e);
            }
        }
    }
}
