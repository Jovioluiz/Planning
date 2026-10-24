package com.planningapp.web.exception;

import com.planningapp.dto.ErroDTO;
import com.planningapp.service.exception.RecursoNaoEncontradoException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RecursoNaoEncontradoExceptionMapper implements ExceptionMapper<RecursoNaoEncontradoException> {

    @Override
    public Response toResponse(RecursoNaoEncontradoException e) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErroDTO(Response.Status.NOT_FOUND.getStatusCode(), e.getMessage()))
                .build();
    }
}
