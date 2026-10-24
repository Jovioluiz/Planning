package com.planningapp.service.exception;

import jakarta.ejb.ApplicationException;

import java.util.List;

/** Conteúdo enviado com problemas (HTTP 400), com um detalhe por problema (ex.: um por linha do arquivo). */
@ApplicationException(rollback = true)
public class DadosInvalidosException extends RuntimeException {

    private final List<String> detalhes;

    public DadosInvalidosException(String mensagem, List<String> detalhes) {
        super(mensagem);
        this.detalhes = List.copyOf(detalhes);
    }

    public List<String> getDetalhes() {
        return detalhes;
    }
}
