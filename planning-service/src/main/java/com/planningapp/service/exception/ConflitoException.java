package com.planningapp.service.exception;

import jakarta.ejb.ApplicationException;

/** A operação conflita com o estado atual (HTTP 409). Ex.: votar duas vezes na mesma rodada. */
@ApplicationException(rollback = true)
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
