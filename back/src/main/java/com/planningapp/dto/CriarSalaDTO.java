package com.planningapp.dto;

import jakarta.validation.constraints.NotBlank;

public class CriarSalaDTO {

    @NotBlank(message = "Nome da sala é obrigatório")
    private String nome;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
}
