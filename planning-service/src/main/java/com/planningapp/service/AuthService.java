package com.planningapp.service;

import com.planningapp.dto.LoginRequest;
import com.planningapp.dto.RegistrarUsuarioRequest;
import com.planningapp.dto.TokenDTO;
import com.planningapp.dto.UsuarioDTO;
import com.planningapp.model.Perfil;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.UsuarioRepository;
import com.planningapp.security.JwtProvider;
import com.planningapp.security.Papeis;
import com.planningapp.security.SenhaLegada;
import com.planningapp.security.TokenGerado;
import com.planningapp.service.exception.CredenciaisInvalidasException;
import com.planningapp.service.exception.NegocioException;
import com.planningapp.service.exception.RecursoNaoEncontradoException;
import com.planningapp.service.mapper.PlanningMapper;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;

import java.util.Optional;
import java.util.Set;

@Stateless
public class AuthService {

    @Inject
    private UsuarioRepository usuarioRepository;

    @Inject
    private JwtProvider jwtProvider;

    /** Implementação PBKDF2 do próprio Jakarta Security (fornecida pelo WildFly). */
    @Inject
    private Pbkdf2PasswordHash passwordHash;

    @Resource
    private SessionContext sessionContext;

    /**
     * Mesmas regras do Planning original: JOGADOR/OBSERVADOR/TESTE livres, ADMIN só se ainda não houver nenhum,
     * SUPER nunca pela API.
     */
    @PermitAll
    public UsuarioDTO registrar(RegistrarUsuarioRequest request) {
        Perfil perfil = converterPerfil(request.perfil());
        if (perfil == Perfil.SUPER) {
            throw new NegocioException("O perfil SUPER não pode ser criado pela API");
        }
        if (perfil == Perfil.ADMIN && usuarioRepository.existePorPerfil(Perfil.ADMIN)) {
            throw new NegocioException("Já existe um ADMIN cadastrado");
        }

        String nome = request.usuario().trim();
        if (usuarioRepository.existePorUsuario(nome)) {
            throw new NegocioException("O usuário " + nome + " já existe");
        }

        String hash = passwordHash.generate(request.senha().toCharArray());
        return PlanningMapper.toDTO(usuarioRepository.salvar(new Usuario(nome, hash, perfil)));
    }

    @PermitAll
    public TokenDTO login(LoginRequest request) {
        Usuario usuario = usuarioRepository.buscarPorUsuario(request.usuario().trim())
                .filter(u -> senhaConfere(u, request.senha()))
                .orElseThrow(CredenciaisInvalidasException::new);
        // Só depois de conferir a senha, para não revelar a quem não a sabe que o usuário existe
        if (!usuario.isAtivo()) {
            throw new CredenciaisInvalidasException("Usuário desativado. Procure o administrador");
        }

        TokenGerado token = jwtProvider.gerar(usuario.getUsuario(), Set.of(usuario.getPerfil().name()));
        return new TokenDTO(token.token(), token.expiraEm(), PlanningMapper.toDTO(usuario));
    }

    /**
     * Usuários migrados do Planning original ainda têm a senha em BCrypt. Se ela confere, já é regravada em PBKDF2
     * (a transação do login faz o commit), e no próximo login o caminho é o normal.
     */
    private boolean senhaConfere(Usuario usuario, String senha) {
        if (SenhaLegada.ehBcrypt(usuario.getSenhaHash())) {
            boolean confere = SenhaLegada.confere(senha, usuario.getSenhaHash());
            if (confere) {
                usuario.alterarSenha(passwordHash.generate(senha.trim().toCharArray()));
            }
            return confere;
        }
        return passwordHash.verify(senha.toCharArray(), usuario.getSenhaHash());
    }

    /** Dados do usuário dono do token da requisição. */
    @RolesAllowed({Papeis.JOGADOR, Papeis.OBSERVADOR, Papeis.ADMIN, Papeis.SUPER, Papeis.TESTE})
    public UsuarioDTO usuarioLogado() {
        String nome = sessionContext.getCallerPrincipal().getName();
        return usuarioRepository.buscarPorUsuario(nome)
                .map(PlanningMapper::toDTO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + nome));
    }

    /**
     * Papéis atuais de quem apresentou um token válido; vazio se o usuário foi excluído ou desativado.
     *
     * <p>Chamado a cada requisição pelo mecanismo de autenticação. O JWT é stateless: se os papéis viessem
     * só do token, desativar ou rebaixar alguém valeria apenas quando o token expirasse (até 8h). O custo é
     * uma consulta por índice único por requisição.</p>
     */
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Optional<Set<String>> papeisAtuais(String usuario) {
        return usuarioRepository.buscarPorUsuario(usuario)
                .filter(Usuario::isAtivo)
                .map(u -> Set.of(u.getPerfil().name()));
    }

    static Perfil converterPerfil(String perfil) {
        try {
            return Perfil.valueOf(perfil.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NegocioException("Perfil inválido: " + perfil);
        }
    }
}
