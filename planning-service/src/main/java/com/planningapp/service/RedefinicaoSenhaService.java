package com.planningapp.service;

import com.planningapp.dto.EsqueciSenhaRequest;
import com.planningapp.dto.RedefinirSenhaPorLinkRequest;
import com.planningapp.dto.UsuarioDTO;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.UsuarioRepository;
import com.planningapp.security.TokenRedefinicao;
import com.planningapp.service.email.ConfiguracaoEmail;
import com.planningapp.service.email.EmailRedefinicaoSenha;
import com.planningapp.service.email.EnvioEmail;
import com.planningapp.service.exception.NegocioException;
import com.planningapp.service.mapper.PlanningMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * "Esqueci minha senha", pela tela de login (sem token):
 *
 * <ol>
 *   <li>{@link #solicitar}: pelo usuário ou e-mail, gera um token, guarda o hash dele no usuário e manda o link
 *       por e-mail. A resposta é sempre a mesma, exista ou não o usuário, para não revelar quem está cadastrado.</li>
 *   <li>{@link #redefinir}: com o token do link, grava a nova senha. O link vale {@link #VALIDADE} e uma vez só.</li>
 * </ol>
 *
 * Quem não cadastrou e-mail no "Meu perfil" não recebe o link: depende do SUPER ({@code PUT /usuarios/{u}/senha}).
 */
@Stateless
@PermitAll
public class RedefinicaoSenhaService {

    private static final Logger LOG = Logger.getLogger(RedefinicaoSenhaService.class.getName());

    static final Duration VALIDADE = Duration.ofMinutes(30);
    /** Não manda outro e-mail para o mesmo usuário antes disso (evita usar o formulário para encher a caixa dele). */
    static final Duration INTERVALO_MINIMO = Duration.ofMinutes(1);

    @Inject
    private UsuarioRepository usuarioRepository;

    @Inject
    private EnvioEmail envioEmail;

    @Inject
    private Pbkdf2PasswordHash passwordHash;

    public void solicitar(EsqueciSenhaRequest request) {
        ConfiguracaoEmail email = envioEmail.configuracao().orElseThrow(() -> new NegocioException(
                "O envio de e-mails não está configurado no servidor. Peça ao administrador para redefinir a sua senha"));

        Optional<Usuario> encontrado = buscar(request.usuarioOuEmail().trim());
        if (encontrado.isEmpty()) {
            LOG.info("Pedido de redefinição de senha para usuário/e-mail sem cadastro");
            return;
        }
        Usuario usuario = encontrado.get();
        if (!usuario.isAtivo() || usuario.getEmail() == null) {
            LOG.info("Pedido de redefinição de senha de " + usuario.getUsuario() + " ignorado: "
                    + (usuario.isAtivo() ? "sem e-mail cadastrado" : "usuário desativado"));
            return;
        }
        Instant agora = Instant.now();
        if (pedidoRecente(usuario, agora)) {
            LOG.info("Pedido de redefinição de senha de " + usuario.getUsuario() + " repetido em menos de "
                    + INTERVALO_MINIMO.toSeconds() + " s: e-mail não reenviado");
            return;
        }

        String token = TokenRedefinicao.gerar();
        usuario.abrirRedefinicaoSenha(TokenRedefinicao.hash(token), agora.plus(VALIDADE));
        EmailRedefinicaoSenha conteudo = EmailRedefinicaoSenha.montar(usuario.getUsuario(),
                email.linkRedefinicao(token), VALIDADE.toMinutes(), Ambiente.producao());
        envioEmail.enviar(usuario.getEmail(), conteudo.assunto(), conteudo.texto(), conteudo.html());
    }

    public UsuarioDTO redefinir(RedefinirSenhaPorLinkRequest request) {
        Usuario usuario = usuarioRepository.buscarPorRedefinicaoHash(TokenRedefinicao.hash(request.token()))
                .filter(Usuario::isAtivo)
                .filter(u -> u.getRedefinicaoExpiraEm() != null && Instant.now().isBefore(u.getRedefinicaoExpiraEm()))
                .orElseThrow(() -> new NegocioException(
                        "Este link de redefinição é inválido ou expirou. Peça um novo em \"Esqueci minha senha\""));

        usuario.alterarSenha(passwordHash.generate(request.novaSenha().trim().toCharArray()));
        usuario.fecharRedefinicaoSenha();
        LOG.info("Senha de " + usuario.getUsuario() + " redefinida pelo link do e-mail");
        return PlanningMapper.toDTO(usuario);
    }

    /** Com "@" é e-mail; senão, o usuário como digitado ou em maiúsculas (o front grava os nomes assim). */
    private Optional<Usuario> buscar(String chave) {
        if (chave.contains("@")) {
            return usuarioRepository.buscarPorEmail(chave.toLowerCase(Locale.ROOT));
        }
        return usuarioRepository.buscarPorUsuario(chave)
                .or(() -> usuarioRepository.buscarPorUsuario(chave.toUpperCase(Locale.ROOT)));
    }

    private static boolean pedidoRecente(Usuario usuario, Instant agora) {
        Instant expira = usuario.getRedefinicaoExpiraEm();
        return expira != null && agora.isBefore(expira.minus(VALIDADE).plus(INTERVALO_MINIMO));
    }
}
