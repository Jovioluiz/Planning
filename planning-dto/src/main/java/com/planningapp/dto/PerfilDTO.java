package com.planningapp.dto;

import java.time.Instant;

/**
 * O perfil do próprio usuário (tela "Meu perfil"). {@code fotoAtualizadaEm} serve para o front trocar a foto em
 * cache quando ela muda; nulo se não há foto.
 */
public record PerfilDTO(String usuario, String perfil, String email, boolean temFoto, Instant fotoAtualizadaEm) {
}
