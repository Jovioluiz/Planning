package com.planningapp.dto;

import java.time.Instant;

/** Resposta do login. O cliente envia {@code token} no header {@code Authorization: Bearer <token>}. */
public record TokenDTO(String token, String tipo, Instant expiraEm, UsuarioDTO usuario) {

    public TokenDTO(String token, Instant expiraEm, UsuarioDTO usuario) {
        this(token, "Bearer", expiraEm, usuario);
    }
}
