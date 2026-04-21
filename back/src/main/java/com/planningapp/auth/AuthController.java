package com.planningapp.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.planningapp.dto.LoginDTO;
import com.planningapp.entity.User;
import com.planningapp.entity.enums.TipoPerfil;
import com.planningapp.repository.UserRepository;
import com.planningapp.security.JwtTokenProvider;
import com.planningapp.service.TaskService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private TaskService taskService;

    @GetMapping("/jogadores")
    public List<String> listarJogadores() {
        return userRepository.findByTipoPerfil(TipoPerfil.JOGADOR)
                .stream()
                .map(User::getUsuario)
                .toList();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDTO login) {
        String usuario = login.getUsuario().trim();
        String senha = login.getSenha().trim();
        String perfilStr = login.getPerfil().trim().toUpperCase();

        Optional<User> userOpt = userRepository.findByUsuario(usuario);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!passwordEncoder.matches(senha, user.getSenha())) {
                return ResponseEntity.status(401)
                        .body(Map.of("success", false, "message", "Senha incorreta."));
            }
            String token = tokenProvider.generateToken(user.getUsuario(), user.getTipoPerfil().name());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Login realizado com sucesso",
                    "perfil", user.getTipoPerfil(),
                    "token", token,
                    "cadastrado", false
            ));
        }

        TipoPerfil perfil;
        try {
            perfil = TipoPerfil.valueOf(perfilStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Perfil inválido."));
        }

        if (perfil == TipoPerfil.ADMIN) {
            return ResponseEntity.status(403)
                    .body(Map.of("success", false,
                            "message", "Administradores não podem ser criados automaticamente. Contate o responsável."));
        }

        User novoUser = new User();
        novoUser.setUsuario(usuario);
        novoUser.setSenha(passwordEncoder.encode(senha));
        novoUser.setTipoPerfil(perfil);
        userRepository.save(novoUser);
        log.info("Novo usuário criado via login: {} ({})", usuario, perfil);

        String token = tokenProvider.generateToken(usuario, perfil.name());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Usuário cadastrado e logado com sucesso",
                "perfil", perfil,
                "token", token,
                "cadastrado", true
        ));
    }

    @PostMapping("/selecionar-sprint")
    public ResponseEntity<?> selecionarSprint(@RequestBody Map<String, String> body, Authentication auth) {
        String sprint = body.get("sprint");
        if (sprint == null || sprint.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Sprint inválida."));
        }
        taskService.vincularJogadorASprint(auth.getName(), sprint);
        return ResponseEntity.ok(Map.of("success", true, "message", "Sprint selecionada com sucesso."));
    }
}
