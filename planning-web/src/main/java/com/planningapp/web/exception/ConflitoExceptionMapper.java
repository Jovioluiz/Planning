package com.planningapp.web.exception;

import com.planningapp.service.exception.ConflitoException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConflitoExceptionMapper implements ExceptionMapper<ConflitoException> {

    @Override
    public Response toResponse(ConflitoException e) {
        return Erros.resposta(Response.Status.CONFLICT, e.getMessage());
    }
}
