package com.planningapp.dto;

// CORRIGIDO: DTO unificado para respostas de estimativas.
// Antes havia inconsistência: /listar usava "user"/"Pontos"/"Horas"
// e /resumo-votos usava "participante"/"pontos"/"horas".
public class EstimativaResponseDTO {
    private String participante;
    private Object pontos;
    private Object horas;
    private boolean revealed;

    public EstimativaResponseDTO(String participante, Object pontos, Object horas, boolean revealed) {
        this.participante = participante;
        this.pontos = pontos;
        this.horas = horas;
        this.revealed = revealed;
    }

    public String getParticipante() { return participante; }
    public Object getPontos() { return pontos; }
    public Object getHoras() { return horas; }
    public boolean isRevealed() { return revealed; }
}
