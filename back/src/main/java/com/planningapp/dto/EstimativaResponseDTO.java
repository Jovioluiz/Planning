package com.planningapp.dto;

public class EstimativaResponseDTO {
    private String participante;
    private Object pontos;
    private Object horas;
    private boolean revealed;
    private boolean horasReveladas;
    private Integer rodada;
    private Long segundosPontos;
    private Long segundosHoras;

    public EstimativaResponseDTO(String participante, Object pontos, Object horas,
                                  boolean revealed, boolean horasReveladas, Integer rodada,
                                  Long segundosPontos, Long segundosHoras) {
        this.participante = participante;
        this.pontos = pontos;
        this.horas = horas;
        this.revealed = revealed;
        this.horasReveladas = horasReveladas;
        this.rodada = rodada != null ? rodada : 1;
        this.segundosPontos = segundosPontos;
        this.segundosHoras = segundosHoras;
    }

    public String getParticipante() { return participante; }
    public Object getPontos() { return pontos; }
    public Object getHoras() { return horas; }
    public boolean isRevealed() { return revealed; }
    public boolean isHorasReveladas() { return horasReveladas; }
    public Integer getRodada() { return rodada; }
    public Long getSegundosPontos() { return segundosPontos; }
    public Long getSegundosHoras() { return segundosHoras; }
}
