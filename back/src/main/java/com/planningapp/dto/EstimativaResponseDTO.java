package com.planningapp.dto;

public class EstimativaResponseDTO {
    private String participante;
    private Object pontos;
    private Object horas;
    private boolean revealed;
    private boolean horasReveladas;

    public EstimativaResponseDTO(String participante, Object pontos, Object horas, boolean revealed, boolean horasReveladas) {
        this.participante = participante;
        this.pontos = pontos;
        this.horas = horas;
        this.revealed = revealed;
        this.horasReveladas = horasReveladas;
    }

    public String getParticipante() { return participante; }
    public Object getPontos() { return pontos; }
    public Object getHoras() { return horas; }
    public boolean isRevealed() { return revealed; }
    public boolean isHorasReveladas() { return horasReveladas; }
}
