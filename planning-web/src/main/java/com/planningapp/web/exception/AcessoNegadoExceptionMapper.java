package com.planningapp.web.exception;

import com.planningapp.service.exception.AcessoNegadoException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AcessoNegadoExceptionMapper implements ExceptionMapper<AcessoNegadoException> {

    @Override
    public Response toResponse(AcessoNegadoException e) {
        return Erros.resposta(Response.Status.FORBIDDEN, e.getMessage());
    }
}
