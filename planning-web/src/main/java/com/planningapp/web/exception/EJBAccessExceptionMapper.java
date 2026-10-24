package com.planningapp.web.exception;

import jakarta.ejb.EJBAccessException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * O container lança {@link EJBAccessException} quando o {@code @RolesAllowed} de um EJB barra a chamada.
 * Sem usuário na requisição, a resposta é 401 (faltou token ou ele é inválido); com usuário, é 403 (papel insuficiente).
 */
@Provider
public class EJBAccessExceptionMapper implements ExceptionMapper<EJBAccessException> {

    @Context
    private SecurityContext securityContext;

    @Override
    public Response toResponse(EJBAccessException e) {
        if (securityContext.getUserPrincipal() == null) {
            return Erros.naoAutenticado("Token ausente, inválido ou expirado");
        }
        return Erros.resposta(Response.Status.FORBIDDEN, "Seu perfil não tem permissão para esta operação");
    }
}
