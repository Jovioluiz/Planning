package com.planningapp.controller;

import com.planningapp.service.SalaService;
import com.planningapp.service.SessaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessoes")
public class SessaoController {

    @Autowired
    private SessaoService sessaoService;

    @Autowired
    private SalaService salaService;

    /** Sem salaId: todos os online (legado). Com salaId: apenas quem está naquela sala. */
    @GetMapping("/online")
    public List<String> getUsuariosOnline(@RequestParam(required = false) Long salaId) {
        if (salaId != null) return sessaoService.getUsuariosOnlineDaSala(salaId);
        return sessaoService.getUsuariosOnline();
    }

    /** Marca a sala em que o usuário autenticado está agora (moderador ou membro). */
    @PostMapping("/sala/{salaId}")
    public ResponseEntity<?> registrarSala(@PathVariable Long salaId, Authentication auth) {
        if (!salaService.usuariosDaSala(salaId).contains(auth.getName())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Usuário não pertence à sala"));
        }
        sessaoService.registrarSalaDoUsuario(auth.getName(), salaId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
