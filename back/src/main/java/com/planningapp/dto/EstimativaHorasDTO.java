package com.planningapp.dto;

public class EstimativaHorasDTO {

	private String participante;
    private double horas;
    
	public double getHoras() {
		return horas;
	}
	public void setHoras(double horas) {
		this.horas = horas;
	}
	public String getParticipante() {
		return participante;
	}
	public void setParticipante(String participante) {
		this.participante = participante;
	}
}
