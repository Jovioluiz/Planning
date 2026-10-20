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

import jakarta.annotation.PreDestroy;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class SessaoService {

    // Tempo de espera antes de inativar a sala após o moderador desconectar.
    // Tolera refreshes de página sem deslogar os players.
    private static final long GRACE_PERIOD_SECONDS = 15;

    // username → último instante conectado (para getUsuariosOnline)
    private final ConcurrentHashMap<String, Instant> sessoes = new ConcurrentHashMap<>();

    // sessionId → username  (rastreia cada conexão WebSocket individual)
    private final ConcurrentHashMap<String, String> sessionUsuario = new ConcurrentHashMap<>();

    // username → Set<sessionId>  (permite múltiplas abas abertas)
    private final ConcurrentHashMap<String, Set<String>> usuarioSessoes = new ConcurrentHashMap<>();

    // username → sala em que o usuário está atualmente. Mantido após desconexão para que,
    // ao reconectar (refresh de página), o usuário volte a aparecer na mesma sala.
    private final ConcurrentHashMap<String, Long> usuarioSala = new ConcurrentHashMap<>();

    // Tarefas de inativação de sala agendadas — canceladas se o moderador reconectar
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingInativacoes = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

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

        // Cancela inativação pendente (moderador reconectou antes do período de graça expirar)
        ScheduledFuture<?> pending = pendingInativacoes.remove(username);
        if (pending != null) {
            pending.cancel(false);
        }

        Set<String> sessions = usuarioSessoes.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet());
        boolean primeiraConexao = sessions.isEmpty();
        sessions.add(sessionId);
        sessionUsuario.put(sessionId, username);
        sessoes.put(username, Instant.now());

        if (primeiraConexao) {
            messagingTemplate.convertAndSend("/topic/sessoes",
                    Map.of("acao", "USUARIO_CONECTADO", "usuario", username));
            Long salaId = usuarioSala.get(username);
            if (salaId != null) notificarSala(salaId, "USUARIO_CONECTADO", username);
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

            messagingTemplate.convertAndSend("/topic/sessoes",
                    Map.of("acao", "USUARIO_DESCONECTADO", "usuario", username));
            Long salaAtual = usuarioSala.get(username);
            if (salaAtual != null) notificarSala(salaAtual, "USUARIO_DESCONECTADO", username);

            // Aguarda o período de graça antes de inativar a sala.
            // Se o moderador reconectar (ex.: refresh de página), a tarefa é cancelada em onConnect.
            final String u = username;
            ScheduledFuture<?> task = scheduler.schedule(() -> {
                if (!sessoes.containsKey(u)) {
                    salaService.inativarSalasDeModerador(u).forEach(salaId ->
                        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/sessoes",
                            Map.of("acao", "SALA_INATIVADA", "salaId", salaId))
                    );
                }
                pendingInativacoes.remove(u);
            }, GRACE_PERIOD_SECONDS, TimeUnit.SECONDS);

            ScheduledFuture<?> anterior = pendingInativacoes.put(username, task);
            if (anterior != null) anterior.cancel(false);
        }
    }

    public List<String> getUsuariosOnline() {
        return new ArrayList<>(sessoes.keySet());
    }

    /**
     * Usuários online que estão nesta sala. Quem não tem sala registrada
     * (ex.: sessões reconectadas após restart do backend) entra pelo vínculo
     * de membro/moderador da sala.
     */
    public List<String> getUsuariosOnlineDaSala(Long salaId) {
        Set<String> daSala = salaService.usuariosDaSala(salaId);
        return sessoes.keySet().stream()
                .filter(u -> {
                    Long sala = usuarioSala.get(u);
                    return sala != null ? sala.equals(salaId) : daSala.contains(u);
                })
                .toList();
    }

    public void registrarSalaDoUsuario(String username, Long salaId) {
        Long anterior = usuarioSala.put(username, salaId);
        if (salaId.equals(anterior) || !sessoes.containsKey(username)) return;
        if (anterior != null) notificarSala(anterior, "USUARIO_DESCONECTADO", username);
        notificarSala(salaId, "USUARIO_CONECTADO", username);
    }

    private void notificarSala(Long salaId, String acao, String username) {
        messagingTemplate.convertAndSend("/topic/sala/" + salaId + "/sessoes",
                Map.of("acao", acao, "usuario", username));
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
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
