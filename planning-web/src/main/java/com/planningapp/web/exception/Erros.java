package com.planningapp.web.exception;

import com.planningapp.dto.ErroDTO;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Respostas de erro padronizadas, usadas pelos ExceptionMappers. */
final class Erros {

    private Erros() {
    }

    static Response resposta(Response.Status status, String mensagem) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErroDTO(status.getStatusCode(), mensagem))
                .build();
    }

    /** 401 com o header {@code WWW-Authenticate}, que diz ao cliente qual esquema de autenticação usar. */
    static Response naoAutenticado(String mensagem) {
        return Response.fromResponse(resposta(Response.Status.UNAUTHORIZED, mensagem))
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .build();
    }
}
