package com.planningapp.dto;

import java.util.List;

/**
 * O chamado como está agora no Redmine, para o modal de finalização vir preenchido, e as opções dos campos.
 *
 * @param pontos           campo "Planning" (nulo se vazio ou não numérico)
 * @param horas            "Tempo estimado"
 * @param horasTeste       campo "EstimativaTeste" (nulo se vazio ou não numérico)
 * @param versaoMerge      campo "Versão Merge"
 * @param prioridadeId     prioridade atual
 * @param dataPrevista     {@code due_date} ({@code yyyy-MM-dd}) ou nulo
 * @param projetoTrabalho  campo "Projeto de Trabalho"
 * @param prioridades      prioridades ativas do Redmine (vazia se não foi possível buscar)
 * @param projetosTrabalho valores possíveis do "Projeto de Trabalho": os da definição do campo (só uma chave de
 *                         administrador do Redmine lê) ou, sem ela, os já usados nos chamados do projeto
 * @param versoesMerge     idem para a "Versão Merge", da mais nova para a mais antiga
 */
public record DadosEnvioRedmineDTO(Integer pontos, Double horas, Integer horasTeste, String versaoMerge,
                                   Integer prioridadeId, String dataPrevista, String projetoTrabalho,
                                   List<OpcaoRedmineDTO> prioridades, List<String> projetosTrabalho,
                                   List<String> versoesMerge) {

    /** Item de uma lista do Redmine (ex.: prioridade). */
    public record OpcaoRedmineDTO(int id, String nome) {
    }
}
