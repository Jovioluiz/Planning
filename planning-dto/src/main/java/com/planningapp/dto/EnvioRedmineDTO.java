package com.planningapp.dto;

import java.util.List;

/**
 * Resultado do envio da estimativa de uma tarefa ao chamado do Redmine. Conferido relendo o chamado: o Redmine
 * aceita a atualização e ignora em silêncio os campos que o usuário da chave não pode alterar.
 *
 * @param chamado  número do chamado
 * @param gravados campos confirmados no chamado, como "Tempo estimado: 5"
 * @param avisos   o que não foi gravado e por quê (vazio quando tudo foi gravado)
 */
public record EnvioRedmineDTO(String chamado, List<String> gravados, List<String> avisos) {
}
