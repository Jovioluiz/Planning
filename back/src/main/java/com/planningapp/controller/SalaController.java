package com.planningapp.controller;

import com.planningapp.dto.CriarSalaDTO;
import com.planningapp.dto.SalaDTO;
import com.planningapp.entity.Sala;
import com.planningapp.service.SalaService;
import com.planningapp.service.SessaoService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/salas")
public class SalaController {

    @Autowired private SalaService salaService;
    @Autowired private SessaoService sessaoService;

    @PostMapping
    public ResponseEntity<?> criarSala(@Valid @RequestBody CriarSalaDTO dto, Authentication auth) {
        if (!isAdmin(auth)) return forbidden();
        Sala sala = salaService.criarSala(dto.getNome(), auth.getName());
        return ResponseEntity.ok(salaService.toDTO(sala));
    }

    @PostMapping("/{codigo}/entrar")
    public ResponseEntity<?> entrarNaSala(@PathVariable String codigo, Authentication auth) {
        try {
            Sala sala = salaService.entrarNaSala(codigo, auth.getName());
            sessaoService.registrarSalaDoUsuario(auth.getName(), sala.getId());
            return ResponseEntity.ok(salaService.toDTO(sala));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/minhas")
    public ResponseEntity<?> minhasSalas(Authentication auth) {
        if (!isAdmin(auth)) return forbidden();
        List<SalaDTO> dtos = salaService.buscarSalasComReativacao(auth.getName())
                .stream().map(salaService::toDTO).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<?> getSalaByCodigo(@PathVariable String codigo) {
        return salaService.findByCodigo(codigo)
                .map(s -> ResponseEntity.ok(Map.of(
                        "id", s.getId(),
                        "nome", s.getNome(),
                        "codigo", s.getCodigo(),
                        "ativa", s.isAtiva()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("success", false, "message", "Acesso negado"));
    }
}
