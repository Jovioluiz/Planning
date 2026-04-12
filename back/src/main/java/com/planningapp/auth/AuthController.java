package com.planningapp.auth;

import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.planningapp.dto.LoginDTO;
import com.planningapp.repository.UserRepository;
import com.planningapp.security.JwtTokenProvider;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDTO login) {
        String usuario = login.getUsuario().trim();
        String senha = login.getSenha().trim();

        return userRepository.findByUsuario(usuario)
                .filter(user -> passwordEncoder.matches(senha, user.getSenha()))
                .map(user -> {
                    String token = tokenProvider.generateToken(user.getUsuario(), user.getTipoPerfil().name());

                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Login realizado com sucesso",
                            "perfil", user.getTipoPerfil(),
                            "token", token
                    ));
                })
                .orElseGet(() ->
                    ResponseEntity.status(401)
                            .body(Map.of("success", false, "message", "Usuário ou senha inválidos."))
                );
    }
}
