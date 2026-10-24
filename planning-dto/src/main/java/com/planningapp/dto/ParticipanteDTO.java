package com.planningapp.dto;

/**
 * @param ativo  pode votar; {@code false} se o moderador removeu
 * @param online conectado no WebSocket da sala agora
 */
public record ParticipanteDTO(String usuario, boolean ativo, boolean online) {
}
