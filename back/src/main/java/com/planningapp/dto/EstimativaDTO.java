package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class EstimativaDTO {

    @NotBlank(message = "Nome do participante é obrigatório")
    private String participante;

    @NotNull(message = "Pontos são obrigatórios")
    private Integer pontos;

    public String getParticipante() { return participante; }
    public void setParticipante(String participante) { this.participante = participante; }

    public Integer getPontos() { return pontos; }
    public void setPontos(Integer pontos) { this.pontos = pontos; }
}
