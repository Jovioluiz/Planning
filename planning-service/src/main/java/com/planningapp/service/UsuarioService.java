package com.planningapp.service;

import com.planningapp.dto.RegistrarUsuarioRequest;
import com.planningapp.dto.UsuarioGestaoDTO;
import com.planningapp.model.Perfil;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.ConfiguracaoRedmineRepository;
import com.planningapp.persistence.EstimativaRepository;
import com.planningapp.persistence.MembroSalaRepository;
import com.planningapp.persistence.ParticipanteRepository;
import com.planningapp.persistence.SalaRepository;
import com.planningapp.persistence.UsuarioRepository;
import com.planningapp.security.Papeis;
import com.planningapp.service.exception.AcessoNegadoException;
import com.planningapp.service.exception.ConflitoException;
import com.planningapp.service.exception.NegocioException;
import com.planningapp.service.exception.RecursoNaoEncontradoException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;

import java.util.List;

/**
 * Gestão de usuários, só para SUPER (no original: listar e excluir). Os SUPER não aparecem nem podem ser
 * alterados por aqui. As mudanças valem na hora, mesmo para quem já tem token: o mecanismo de autenticação
 * confere o usuário no banco a cada requisição ({@link AuthService#papeisAtuais}).
 */
@Stateless
@RolesAllowed(Papeis.SUPER)
public class UsuarioService {

    @Inject
    private UsuarioRepository usuarioRepository;

    @Inject
    private SalaRepository salaRepository;

    @Inject
    private EstimativaRepository estimativaRepository;

    @Inject
    private MembroSalaRepository membroSalaRepository;

    @Inject
    private ParticipanteRepository participanteRepository;

    @Inject
    private ConfiguracaoRedmineRepository configuracaoRedmineRepository;

    @Inject
    private Pbkdf2PasswordHash passwordHash;

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<UsuarioGestaoDTO> listar() {
        return usuarioRepository.listarExceto(Perfil.SUPER).stream().map(this::toDTO).toList();
    }

    /** Diferente do cadastro público, aqui dá para criar mais de um ADMIN. SUPER, nunca. */
    public UsuarioGestaoDTO criar(RegistrarUsuarioRequest request) {
        Perfil perfil = perfilGerenciavel(request.perfil());
        String nome = request.usuario().trim();
        if (usuarioRepository.existePorUsuario(nome)) {
            throw new ConflitoException("O usuário " + nome + " já existe");
        }
        Usuario usuario = new Usuario(nome, passwordHash.generate(request.senha().toCharArray()), perfil);
        return toDTO(usuarioRepository.salvar(usuario));
    }

    public UsuarioGestaoDTO alterarPerfil(String nome, String novoPerfil) {
        Usuario usuario = buscarGerenciavel(nome);
        Perfil perfil = perfilGerenciavel(novoPerfil);
        if (usuario.getPerfil() == Perfil.ADMIN && perfil != Perfil.ADMIN) {
            exigirSemSalasAtivas(usuario, "deixar de ser ADMIN");
        }
        usuario.alterarPerfil(perfil);
        return toDTO(usuario);
    }

    public UsuarioGestaoDTO redefinirSenha(String nome, String novaSenha) {
        Usuario usuario = buscarGerenciavel(nome);
        usuario.alterarSenha(passwordHash.generate(novaSenha.toCharArray()));
        return toDTO(usuario);
    }

    /** O usuário não faz mais login e o token que ele já tem para de valer na próxima requisição. */
    public UsuarioGestaoDTO desativar(String nome) {
        Usuario usuario = buscarGerenciavel(nome);
        if (usuario.getPerfil() == Perfil.ADMIN) {
            exigirSemSalasAtivas(usuario, "ser desativado");
        }
        usuario.desativar();
        return toDTO(usuario);
    }

    public UsuarioGestaoDTO reativar(String nome) {
        Usuario usuario = buscarGerenciavel(nome);
        usuario.reativar();
        return toDTO(usuario);
    }

    /**
     * Exclusão física, só para quem não tem histórico (votos ou salas). Diferente do original, que apagava
     * direto: aqui isso quebraria as FKs ou apagaria votos das estatísticas. Para os demais, desative.
     */
    public void excluir(String nome) {
        Usuario usuario = buscarGerenciavel(nome);
        if (temHistorico(usuario)) {
            throw new ConflitoException(nome + " tem votos ou salas no histórico. Desative o usuário em vez de excluir");
        }
        participanteRepository.removerDoUsuario(usuario.getId());
        membroSalaRepository.removerDoUsuario(usuario.getId());
        configuracaoRedmineRepository.removerDoUsuario(usuario.getId());
        usuarioRepository.remover(usuario);
    }

    private void exigirSemSalasAtivas(Usuario usuario, String acao) {
        long salas = salaRepository.contarDoModerador(usuario.getId(), true);
        if (salas > 0) {
            throw new ConflitoException(usuario.getUsuario() + " modera " + salas
                    + " sala(s) ativa(s) e não pode " + acao + ". Desative as salas antes");
        }
    }

    private boolean temHistorico(Usuario usuario) {
        return estimativaRepository.existeDoUsuario(usuario.getId())
                || salaRepository.contarDoModerador(usuario.getId(), false) > 0;
    }

    private Usuario buscarGerenciavel(String nome) {
        Usuario usuario = usuarioRepository.buscarPorUsuario(nome)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + nome));
        if (usuario.getPerfil() == Perfil.SUPER) {
            throw new AcessoNegadoException("Usuários SUPER não podem ser alterados pela API");
        }
        return usuario;
    }

    private static Perfil perfilGerenciavel(String valor) {
        Perfil perfil = AuthService.converterPerfil(valor);
        if (perfil == Perfil.SUPER) {
            throw new NegocioException("O perfil SUPER não pode ser atribuído pela API");
        }
        return perfil;
    }

    private UsuarioGestaoDTO toDTO(Usuario u) {
        return new UsuarioGestaoDTO(u.getId(), u.getUsuario(), u.getPerfil().name(), u.isAtivo(), u.getCriadoEm(),
                salaRepository.contarDoModerador(u.getId(), true), temHistorico(u));
    }
}
