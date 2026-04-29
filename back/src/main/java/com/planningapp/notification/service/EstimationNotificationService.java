package com.planningapp.notification.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class EstimationNotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void notificarTodos(String acao, Long taskId) {
        messagingTemplate.convertAndSend("/topic/estimativas", Map.of(
                "acao", acao,
                "taskId", taskId.toString()
        ));
    }

    public void notificarTodos(String acao, Long taskId, Map<String, Object> extra) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>(extra);
        payload.put("acao", acao);
        payload.put("taskId", taskId.toString());
        messagingTemplate.convertAndSend("/topic/estimativas", payload);
    }
}
