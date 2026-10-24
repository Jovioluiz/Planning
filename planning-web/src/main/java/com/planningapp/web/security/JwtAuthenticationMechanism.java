package com.planningapp.web.security;

import com.planningapp.security.JwtProvider;
import com.planningapp.service.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mecanismo de autenticação do Jakarta Security: o container chama {@link #validateRequest} em toda requisição,
 * antes de chegar no JAX-RS. Com um token válido, o usuário e os papéis viram a identidade da requisição,
 * e daí o {@code @RolesAllowed} dos EJBs e o {@code getCallerPrincipal()} passam a funcionar.
 *
 * <p>O token prova quem é o usuário; os papéis vêm do banco ({@link AuthService#papeisAtuais}), para que
 * desativar ou mudar o perfil de alguém valha na hora, sem esperar o token expirar.</p>
 *
 * <p>Sem token, com token inválido/expirado ou de usuário desativado, a requisição segue como anônima. Quem
 * barra é o {@code @RolesAllowed}: o erro volta como JSON pelo {@code EJBAccessExceptionMapper} e passa pelo
 * {@code CorsFilter}. Se respondêssemos 401 aqui, a resposta sairia sem os headers de CORS e o
 * navegador mostraria um erro de CORS em vez do 401.</p>
 */
@ApplicationScoped
public class JwtAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final String PREFIXO = "Bearer ";

    @Inject
    private JwtProvider jwtProvider;

    @Inject
    private AuthService authService;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response,
                                                HttpMessageContext contexto) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, PREFIXO, 0, PREFIXO.length())) {
            return contexto.doNothing();
        }

        return jwtProvider.validar(header.substring(PREFIXO.length()).trim())
                .flatMap(token -> authService.papeisAtuais(token.usuario())
                        .map(papeis -> contexto.notifyContainerAboutLogin(token.usuario(), papeis)))
                .orElseGet(contexto::doNothing);
    }
}
