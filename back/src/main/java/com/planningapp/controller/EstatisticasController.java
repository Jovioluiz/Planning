package com.planningapp.controller;

import com.planningapp.service.EstatisticasService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/estatisticas")
public class EstatisticasController {

    @Autowired private EstatisticasService estatisticasService;

    /**
     * SUPER vê todas as tarefas; ADMIN vê as das salas que modera + tarefas legadas (sem sala).
     * {@code salaId = 0} filtra apenas as tarefas legadas.
     */
    @GetMapping
    public ResponseEntity<?> estatisticas(
            @RequestParam(required = false) Long salaId,
            @RequestParam(required = false) String sprint,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            Authentication auth) {
        if (hasRole(auth, "ROLE_SUPER")) {
            return ResponseEntity.ok(estatisticasService.calcular(null, salaId, sprint, de, ate));
        }
        if (hasRole(auth, "ROLE_ADMIN")) {
            return ResponseEntity.ok(estatisticasService.calcular(auth.getName(), salaId, sprint, de, ate));
        }
        return ResponseEntity.status(403).body(Map.of("success", false, "message", "Acesso negado"));
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }
}
