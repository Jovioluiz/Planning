package com.planningapp.dto;

import java.time.LocalDateTime;

/** Linha da tela de gestão de usuários (só para SUPER). */
public record UsuarioGestaoDTO(Long id, String usuario, String perfil, boolean ativo, LocalDateTime criadoEm,
                               long salasModeradas, boolean temHistorico) {
}
