package com.planningapp.service.exception;

import jakarta.ejb.ApplicationException;

/**
 * Violação de regra de negócio (HTTP 422).
 *
 * <p>{@code @ApplicationException} faz o container repassar a exceção como está, em vez de embrulhá-la
 * em {@code EJBException}, e {@code rollback = true} desfaz a transação.</p>
 */
@ApplicationException(rollback = true)
public class NegocioException extends RuntimeException {

    public NegocioException(String mensagem) {
        super(mensagem);
    }
}
