package com.planningapp.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** Valores finais escolhidos pelo moderador ao finalizar uma tarefa. Todos opcionais. */
public class FinalizarTarefaDTO {

    @PositiveOrZero(message = "Pontos finais não podem ser negativos")
    private Integer pontosFinais;

    @PositiveOrZero(message = "Horas finais não podem ser negativas")
    private Double horasFinais;

    @PositiveOrZero(message = "Horas de teste finais não podem ser negativas")
    private Double horasTesteFinais;

    public Integer getPontosFinais() { return pontosFinais; }
    public void setPontosFinais(Integer pontosFinais) { this.pontosFinais = pontosFinais; }

    public Double getHorasFinais() { return horasFinais; }
    public void setHorasFinais(Double horasFinais) { this.horasFinais = horasFinais; }

    public Double getHorasTesteFinais() { return horasTesteFinais; }
    public void setHorasTesteFinais(Double horasTesteFinais) { this.horasTesteFinais = horasTesteFinais; }
}
