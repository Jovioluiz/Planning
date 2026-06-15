package com.planningapp.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class TaskDTO {

    @NotNull(message = "Número é obrigatório")
    private Long numero;

    private String titulo;
    private String descricao;

    private Integer prioridade;
    private String status;
    private String sprint;
    private Map<String, Object> dadosExtras;

    public Long getNumero() { return numero; }
    public void setNumero(Long numero) { this.numero = numero; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Integer getPrioridade() { return prioridade; }
    public void setPrioridade(Integer prioridade) { this.prioridade = prioridade; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSprint() { return sprint; }
    public void setSprint(String sprint) { this.sprint = sprint; }

    public Map<String, Object> getDadosExtras() { return dadosExtras; }
    public void setDadosExtras(Map<String, Object> dadosExtras) { this.dadosExtras = dadosExtras; }
}
