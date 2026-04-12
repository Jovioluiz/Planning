package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class EstimativaHorasDTO {

    @NotBlank(message = "Nome do participante é obrigatório")
    private String participante;

    @NotNull(message = "Horas são obrigatórias")
    @Positive(message = "Horas devem ser um valor positivo")
    private Double horas;

    public String getParticipante() { return participante; }
    public void setParticipante(String participante) { this.participante = participante; }

    public Double getHoras() { return horas; }
    public void setHoras(Double horas) { this.horas = horas; }
}
