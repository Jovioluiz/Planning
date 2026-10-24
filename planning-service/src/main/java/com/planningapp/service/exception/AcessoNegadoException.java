package com.planningapp.service.exception;

import jakarta.ejb.ApplicationException;

/** Usuário autenticado, mas sem permissão para a operação (HTTP 403). Ex.: não é o moderador da sala. */
@ApplicationException(rollback = true)
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
