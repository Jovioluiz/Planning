package com.planningapp.dto;

/** Voto de horas de teste de um testador. {@code horas} vem nulo enquanto oculto para quem consulta. */
public record VotoTesteDTO(String usuario, boolean votou, Integer horas) {
}
