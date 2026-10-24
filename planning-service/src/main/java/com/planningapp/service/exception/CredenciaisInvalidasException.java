package com.planningapp.service.exception;

import jakarta.ejb.ApplicationException;

/** Usuário ou senha incorretos (HTTP 401). A mensagem não diz qual dos dois, de propósito. */
@ApplicationException(rollback = true)
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Usuário ou senha inválidos");
    }

    /** Para quando a senha confere mas o acesso continua negado (ex.: usuário desativado). */
    public CredenciaisInvalidasException(String mensagem) {
        super(mensagem);
    }
}
