package com.planningapp.controller;

import com.planningapp.entity.User;
import com.planningapp.entity.enums.TipoPerfil;
import com.planningapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/usuarios")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> listarUsuarios() {
        if (!isSuper()) {
            return ResponseEntity.status(403)
                    .body(Map.of("success", false, "message", "Acesso restrito ao super usuário."));
        }

        List<Map<String, String>> usuarios = userRepository.findAll().stream()
                .filter(u -> u.getTipoPerfil() != TipoPerfil.SUPER)
                .map(u -> Map.of("usuario", u.getUsuario(), "perfil", u.getTipoPerfil().name()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(usuarios);
    }

    @DeleteMapping("/{usuario}")
    public ResponseEntity<?> excluirUsuario(@PathVariable String usuario) {
        if (!isSuper()) {
            return ResponseEntity.status(403)
                    .body(Map.of("success", false, "message", "Acesso restrito ao super usuário."));
        }

        Optional<User> userOpt = userRepository.findByUsuario(usuario);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("success", false, "message", "Usuário não encontrado."));
        }

        if (userOpt.get().getTipoPerfil() == TipoPerfil.SUPER) {
            return ResponseEntity.status(403)
                    .body(Map.of("success", false, "message", "Não é possível excluir o super usuário."));
        }

        userRepository.delete(userOpt.get());
        return ResponseEntity.ok(Map.of("success", true, "message", "Usuário excluído com sucesso."));
    }

    private boolean isSuper() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER"));
    }
}
