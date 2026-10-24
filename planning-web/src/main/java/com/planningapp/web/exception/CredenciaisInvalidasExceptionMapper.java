package com.planningapp.web.exception;

import com.planningapp.service.exception.CredenciaisInvalidasException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CredenciaisInvalidasExceptionMapper implements ExceptionMapper<CredenciaisInvalidasException> {

    @Override
    public Response toResponse(CredenciaisInvalidasException e) {
        return Erros.naoAutenticado(e.getMessage());
    }
}
