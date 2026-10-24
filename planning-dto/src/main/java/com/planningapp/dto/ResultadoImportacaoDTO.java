package com.planningapp.dto;

import java.util.List;

/**
 * @param importadas tarefas criadas, já na fila
 * @param ignoradas  números que já existiam na sala ou se repetiam no próprio arquivo (fica a primeira)
 */
public record ResultadoImportacaoDTO(String mensagem, List<TarefaDTO> importadas, List<String> ignoradas) {
}
