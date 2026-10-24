package com.planningapp.web.exception;

import com.planningapp.dto.ErroDTO;
import com.planningapp.service.exception.DadosInvalidosException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DadosInvalidosExceptionMapper implements ExceptionMapper<DadosInvalidosException> {

    @Override
    public Response toResponse(DadosInvalidosException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErroDTO(Response.Status.BAD_REQUEST.getStatusCode(), e.getMessage(), e.getDetalhes()))
                .build();
    }
}
