package com.planningapp.config;

import com.planningapp.entity.User;
import com.planningapp.entity.enums.TipoPerfil;
import com.planningapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Cria usuários padrão na primeira execução caso não existam.
// IMPORTANTE: altere as senhas abaixo antes de ir para produção,
// ou gerencie usuários via endpoint administrativo protegido.
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // criarSeNaoExistir("admin",    "admin123",  TipoPerfil.ADMIN);
        // criarSeNaoExistir("jogador1", "jogador123", TipoPerfil.JOGADOR);
        // criarSeNaoExistir("obs1",     "obs123",     TipoPerfil.OBSERVADOR);
    }

    private void criarSeNaoExistir(String usuario, String senha, TipoPerfil perfil) {
        if (userRepository.findByUsuario(usuario).isEmpty()) {
            User user = new User();
            user.setUsuario(usuario);
            // CORRIGIDO: senha armazenada como BCrypt hash — nunca texto puro.
            user.setSenha(passwordEncoder.encode(senha));
            user.setTipoPerfil(perfil);
            userRepository.save(user);
            log.info("[Seed] Usuário criado: {} ({})", usuario, perfil);
        }
    }
}
