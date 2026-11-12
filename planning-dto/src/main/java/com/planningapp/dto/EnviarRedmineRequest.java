package com.planningapp.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Campos do chamado confirmados pelo moderador no modal de finalização, enviados ao Redmine junto com a estimativa
 * (horas, pontos e horas de teste vêm da tarefa finalizada). Só o que mudou em relação ao chamado é enviado.
 *
 * <p>Nulo = não mexer no campo; texto vazio = limpar (versão, data prevista e projeto de trabalho).</p>
 *
 * @param versaoMerge     campo personalizado "Versão Merge"
 * @param prioridadeId    id da prioridade do Redmine ({@code priority_id})
 * @param dataPrevista    {@code due_date}, no formato {@code yyyy-MM-dd}
 * @param projetoTrabalho campo personalizado "Projeto de Trabalho"
 */
public record EnviarRedmineRequest(
        @Size(max = 100, message = "A versão do merge pode ter até 100 caracteres")
        String versaoMerge,
        @Positive
        Integer prioridadeId,
        @Pattern(regexp = "^$|\\d{4}-\\d{2}-\\d{2}", message = "A data prevista precisa estar no formato aaaa-mm-dd")
        String dataPrevista,
        @Size(max = 255, message = "O projeto de trabalho pode ter até 255 caracteres")
        String projetoTrabalho) {
}
