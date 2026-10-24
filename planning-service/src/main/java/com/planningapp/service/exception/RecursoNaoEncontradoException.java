package com.planningapp.service.exception;

import jakarta.ejb.ApplicationException;

/** Recurso inexistente (HTTP 404). */
@ApplicationException(rollback = true)
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
