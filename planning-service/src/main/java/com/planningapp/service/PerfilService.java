package com.planningapp.service;

import com.planningapp.dto.AlterarEmailRequest;
import com.planningapp.dto.AlterarSenhaRequest;
import com.planningapp.dto.FotoDTO;
import com.planningapp.dto.PerfilDTO;
import com.planningapp.model.FotoUsuario;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.FotoUsuarioRepository;
import com.planningapp.persistence.UsuarioRepository;
import com.planningapp.security.Papeis;
import com.planningapp.security.SenhaLegada;
import com.planningapp.service.exception.ConflitoException;
import com.planningapp.service.exception.NegocioException;
import com.planningapp.service.exception.RecursoNaoEncontradoException;
import com.planningapp.service.perfil.ImagemPerfil;
import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;

import java.util.Locale;
import java.util.Optional;

/**
 * "Meu perfil": o próprio usuário logado troca a senha, o e-mail e a foto. Sempre o dono do token; ninguém mexe
 * no perfil de outro por aqui (o SUPER redefine senhas pelo {@link UsuarioService}).
 */
@Stateless
@RolesAllowed({Papeis.JOGADOR, Papeis.OBSERVADOR, Papeis.ADMIN, Papeis.SUPER, Papeis.TESTE})
public class PerfilService {

    @Inject
    private UsuarioRepository usuarioRepository;

    @Inject
    private FotoUsuarioRepository fotoRepository;

    @Inject
    private Pbkdf2PasswordHash passwordHash;

    @Resource
    private SessionContext sessionContext;

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PerfilDTO meuPerfil() {
        return toDTO(usuarioLogado());
    }

    public void alterarSenha(AlterarSenhaRequest request) {
        Usuario usuario = usuarioLogado();
        if (!senhaConfere(usuario, request.senhaAtual())) {
            throw new NegocioException("A senha atual não confere");
        }
        usuario.alterarSenha(passwordHash.generate(request.novaSenha().trim().toCharArray()));
        // Um link de "Esqueci minha senha" ainda aberto não deve trocar a senha que acabou de ser escolhida
        usuario.fecharRedefinicaoSenha();
    }

    public PerfilDTO alterarEmail(AlterarEmailRequest request) {
        Usuario usuario = usuarioLogado();
        String email = normalizarEmail(request.email());
        if (email != null) {
            usuarioRepository.buscarPorEmail(email)
                    .filter(outro -> !outro.getId().equals(usuario.getId()))
                    .ifPresent(outro -> {
                        throw new ConflitoException("Este e-mail já está cadastrado para outro usuário");
                    });
        }
        usuario.alterarEmail(email);
        return toDTO(usuario);
    }

    public PerfilDTO salvarFoto(byte[] conteudo) {
        String tipo = ImagemPerfil.tipo(conteudo);
        Usuario usuario = usuarioLogado();
        FotoUsuario foto = fotoRepository.buscar(usuario.getId()).orElseGet(() -> new FotoUsuario(usuario.getId()));
        foto.alterar(conteudo, tipo);
        fotoRepository.salvar(foto);
        return toDTO(usuario, Optional.of(foto));
    }

    public PerfilDTO removerFoto() {
        Usuario usuario = usuarioLogado();
        fotoRepository.removerDoUsuario(usuario.getId());
        return toDTO(usuario, Optional.empty());
    }

    /** Foto de qualquer usuário (para os avatares da sala); vazio se ele não tem foto ou não existe. */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Optional<FotoDTO> foto(String nomeUsuario) {
        return usuarioRepository.buscarPorUsuario(nomeUsuario)
                .flatMap(u -> fotoRepository.buscar(u.getId()))
                .map(f -> new FotoDTO(f.getConteudo(), f.getTipo()));
    }

    /** E-mail em minúsculas e sem espaços nas pontas; nulo se vazio. */
    static String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Como no login: hashes BCrypt migrados do Planning original também valem. */
    private boolean senhaConfere(Usuario usuario, String senha) {
        if (SenhaLegada.ehBcrypt(usuario.getSenhaHash())) {
            return SenhaLegada.confere(senha, usuario.getSenhaHash());
        }
        return passwordHash.verify(senha.toCharArray(), usuario.getSenhaHash());
    }

    private Usuario usuarioLogado() {
        String nome = sessionContext.getCallerPrincipal().getName();
        return usuarioRepository.buscarPorUsuario(nome)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + nome));
    }

    private PerfilDTO toDTO(Usuario usuario) {
        return toDTO(usuario, fotoRepository.buscar(usuario.getId()));
    }

    private static PerfilDTO toDTO(Usuario usuario, Optional<FotoUsuario> foto) {
        return new PerfilDTO(usuario.getUsuario(), usuario.getPerfil().name(), usuario.getEmail(), foto.isPresent(),
                foto.map(FotoUsuario::getAtualizadaEm).orElse(null));
    }
}
