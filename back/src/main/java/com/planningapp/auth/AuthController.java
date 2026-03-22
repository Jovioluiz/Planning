package com.planningapp.auth;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.planningapp.dto.LoginDTO;
import com.planningapp.repository.UserRepository;

// CORRIGIDO: removido @CrossOrigin — CORS centralizado em WebConfig.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO login) {

        if (login.getUsuario() == null || login.getSenha() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Usuário e senha são obrigatórios."));
        }

        String usuario = login.getUsuario().trim();
        String senha = login.getSenha().trim();

        return userRepository.findByUsuario(usuario)
                // CORRIGIDO: usa BCrypt para comparar — nunca texto puro.
                .filter(user -> passwordEncoder.matches(senha, user.getSenha()))
                .map(user -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Login realizado com sucesso",
                        "perfil", user.getTipoPerfil())))
                .orElseGet(() ->
                    // CORRIGIDO: retorna 401 em vez de criar automaticamente um novo usuário
                    // com o perfil enviado pelo cliente (vulnerabilidade crítica anterior).
                    ResponseEntity.status(401)
                            .body(Map.of("success", false, "message", "Usuário ou senha inválidos."))
                );
    }
}
