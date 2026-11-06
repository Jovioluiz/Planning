package com.planningapp.dto;

/**
 * Em que ambiente o servidor está (variável {@code PLANNING_AMBIENTE}): "producao" ou "testes". O front mostra uma
 * faixa quando não é produção, para ninguém votar no banco errado sem perceber.
 */
public record AmbienteDTO(String ambiente, boolean producao) {
}
