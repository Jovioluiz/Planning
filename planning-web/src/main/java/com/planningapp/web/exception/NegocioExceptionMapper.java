package com.planningapp.web.exception;

import com.planningapp.dto.ErroDTO;
import com.planningapp.service.exception.NegocioException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NegocioExceptionMapper implements ExceptionMapper<NegocioException> {

    private static final int UNPROCESSABLE_ENTITY = 422;

    @Override
    public Response toResponse(NegocioException e) {
        return Response.status(UNPROCESSABLE_ENTITY)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErroDTO(UNPROCESSABLE_ENTITY, e.getMessage()))
                .build();
    }
}
