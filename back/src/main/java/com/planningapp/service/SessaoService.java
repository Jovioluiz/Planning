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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessaoService {

    private final ConcurrentHashMap<String, Instant> sessoes = new ConcurrentHashMap<>();

    @Lazy
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        String username = extrairUsername(event.getUser(), event.getMessage());
        if (username != null) {
            sessoes.put(username, Instant.now());
            messagingTemplate.convertAndSend("/topic/sessoes",
                    Map.of("acao", "USUARIO_CONECTADO", "usuario", username));
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String username = extrairUsername(event.getUser(), event.getMessage());
        if (username != null) {
            sessoes.remove(username);
            messagingTemplate.convertAndSend("/topic/sessoes",
                    Map.of("acao", "USUARIO_DESCONECTADO", "usuario", username));
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

    public List<String> getUsuariosOnline() {
        return new ArrayList<>(sessoes.keySet());
    }
}
