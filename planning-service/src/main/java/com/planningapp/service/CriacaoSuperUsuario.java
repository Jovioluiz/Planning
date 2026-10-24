package com.planningapp.service;

import com.planningapp.model.Perfil;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.DependsOn;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;

import java.util.logging.Logger;

/**
 * Cria o usuário SUPER no deploy, a partir de {@code SUPER_USUARIO} e {@code SUPER_SENHA}, se ainda não existir
 * nenhum. No original o SUPER só era criado direto no banco; aqui isso é inviável, porque a senha fica em hash
 * PBKDF2.
 *
 * <p>{@code @Singleton @Startup}: o container instancia o EJB ao subir a aplicação e roda o
 * {@code @PostConstruct} dentro de uma transação (o padrão {@code REQUIRED} vale para os callbacks de ciclo
 * de vida de um Singleton). É o equivalente a um {@code CommandLineRunner} do Spring.</p>
 *
 * <p>Depois de criado, a senha pode sair do ambiente: o SUPER já existe e a variável não é mais lida.</p>
 */
@Singleton
@DependsOn("MigracaoBanco") // a tabela usuarios precisa existir
@Startup
public class CriacaoSuperUsuario {

    private static final Logger LOG = Logger.getLogger(CriacaoSuperUsuario.class.getName());

    @Inject
    private UsuarioRepository usuarioRepository;

    @Inject
    private Pbkdf2PasswordHash passwordHash;

    @PostConstruct
    void criarSeNecessario() {
        if (usuarioRepository.existePorPerfil(Perfil.SUPER)) {
            return;
        }
        String usuario = System.getenv("SUPER_USUARIO");
        String senha = System.getenv("SUPER_SENHA");
        if (usuario == null || usuario.isBlank() || senha == null || senha.isBlank()) {
            LOG.info("Nenhum SUPER cadastrado. Para criar um, defina SUPER_USUARIO e SUPER_SENHA e reinicie a aplicação");
            return;
        }
        if (senha.length() < 6) {
            LOG.warning("SUPER_SENHA precisa ter pelo menos 6 caracteres. O SUPER não foi criado");
            return;
        }
        if (usuarioRepository.existePorUsuario(usuario.trim())) {
            LOG.warning("Já existe um usuário " + usuario.trim() + " (não SUPER). O SUPER não foi criado");
            return;
        }
        usuarioRepository.salvar(new Usuario(usuario.trim(), passwordHash.generate(senha.toCharArray()), Perfil.SUPER));
        LOG.info("Usuário SUPER " + usuario.trim() + " criado");
    }
}
