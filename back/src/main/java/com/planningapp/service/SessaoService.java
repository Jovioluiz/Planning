package com.planningapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessaoService {

    // username → último instante conectado (para getUsuariosOnline)
    private final ConcurrentHashMap<String, Instant> sessoes = new ConcurrentHashMap<>();

    // sessionId → username  (rastreia cada conexão WebSocket individual)
    private final ConcurrentHashMap<String, String> sessionUsuario = new ConcurrentHashMap<>();

    // username → Set<sessionId>  (permite múltiplas abas abertas)
    private final ConcurrentHashMap<String, Set<String>> usuarioSessoes = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> usuarioSala = new ConcurrentHashMap<>();

    @Lazy
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Lazy
    @Autowired
    private SalaService salaService;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        String sessionId = extrairSessionId(event.getMessage());
        String username  = extrairUsername(event.getUser(), event.getMessage());
        if (username == null || sessionId == null) return;

        Set<String> sessions = usuarioSessoes.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet());
        boolean primeiraConexao = sessions.isEmpty();
        sessions.add(sessionId);
        sessionUsuario.put(sessionId, username);
        sessoes.put(username, Instant.now());

        if (primeiraConexao) {
            messagingTemplate.convertAndSend("/topic/sessoes",
                    Map.of("acao", "USUARIO_CONECTADO", "usuario", username));
            // Reativa salas inativas com tarefas em aberto ao reconectar
            salaService.reativarSalasComTarefasAbertas(username);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = extrairSessionId(event.getMessage());
        if (sessionId == null) return;

        String username = sessionUsuario.remove(sessionId);
        if (username == null) return;

        Set<String> sessions = usuarioSessoes.get(username);
        if (sessions != null) sessions.remove(sessionId);

        // Só considera totalmente desconectado quando não há mais sessões abertas
        boolean semSessoes = sessions == null || sessions.isEmpty();
        if (semSessoes) {
            usuarioSessoes.remove(username);
            sessoes.remove(username);
            usuarioSala.remove(username);

            messagingTemplate.convertAndSend("/topic/sessoes",
                    Map.of("acao", "USUARIO_DESCONECTADO", "usuario", username));

            salaService.inativarSalasDeModerador(username).forEach(salaId ->
                messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/sessoes",
                    Map.of("acao", "SALA_INATIVADA", "salaId", salaId))
            );
        }
    }

    public List<String> getUsuariosOnline() {
        return new ArrayList<>(sessoes.keySet());
    }

    public void registrarSalaDoUsuario(String username, Long salaId) {
        usuarioSala.put(username, salaId);
    }

    private String extrairSessionId(Message<?> message) {
        try {
            return StompHeaderAccessor.wrap(message).getSessionId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extrairUsername(Principal user, Message<?> message) {
        if (user != null) return user.getName();
        try {
            StompHeaderAccessor sha = StompHeaderAccessor.wrap(message);
            Principal p = sha.getUser();
            return p != null ? p.getName() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
