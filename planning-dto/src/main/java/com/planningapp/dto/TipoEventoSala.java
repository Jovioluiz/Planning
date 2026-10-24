package com.planningapp.dto;

/** Tipos de mensagem enviados pelo WebSocket de uma sala. */
public enum TipoEventoSala {
    TAREFA_CRIADA,
    /** O moderador importou várias tarefas de uma vez (um aviso só, não um por tarefa): recarregue a lista. */
    TAREFAS_IMPORTADAS,
    TAREFA_LIBERADA,
    TAREFA_FINALIZADA,
    /** O moderador encerrou a tarefa sem estimativa; a sala fica livre para liberar outra. */
    TAREFA_PULADA,
    TAREFA_EXCLUIDA,
    SALA_DESATIVADA,
    /** Alguém votou. Traz {@code usuario} e a tarefa, nunca o valor do voto. */
    VOTO_REGISTRADO,
    PONTOS_REVELADOS,
    HORAS_LIBERADAS,
    HORAS_REVELADAS,
    NOVA_RODADA,
    /** Um testador votou nas horas de teste. Traz {@code usuario}, nunca o valor. */
    VOTO_TESTE_REGISTRADO,
    HORAS_TESTE_LIBERADAS,
    HORAS_TESTE_REVELADAS,
    /** Todos os jogadores online votaram na fase aberta ({@code tarefa.fase}): o moderador já pode revelar. */
    TODOS_VOTARAM,
    /** Todos os testadores online votaram nas horas de teste. */
    TODOS_TESTADORES_VOTARAM,
    /** O moderador removeu {@code usuario} da tarefa: ele não vota mais e o voto da rodada atual foi descartado. */
    PARTICIPANTE_REMOVIDO,
    /** O moderador incluiu ou readmitiu {@code usuario} na tarefa. */
    PARTICIPANTE_ADICIONADO,
    /** Alguém entrou ou saiu da sala; {@code online} traz a lista atualizada. */
    PRESENCA
}
