package com.planningapp.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tarefas")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long numero;
    private String titulo;
    private String descricao;
    private Integer prioridade;
    private String status;
    private boolean estimada = false;
    private boolean liberada = false;

    // CORRIGIDO: campos que o frontend usa para controlar estado da votação
    // mas que não existiam na entidade — causava NullPointerException silencioso.
    private boolean pontosRevelados = false;
    private boolean horasReveladas = false;
    private boolean horasLiberadas = false;
    private String sprint;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public boolean getEstimada() { return estimada; }
    public void setEstimada(boolean estimada) { this.estimada = estimada; }

    public boolean isLiberada() { return liberada; }
    public void setLiberada(boolean liberada) { this.liberada = liberada; }

    public boolean isPontosRevelados() { return pontosRevelados; }
    public void setPontosRevelados(boolean pontosRevelados) { this.pontosRevelados = pontosRevelados; }

    public boolean isHorasReveladas() { return horasReveladas; }
    public void setHorasReveladas(boolean horasReveladas) { this.horasReveladas = horasReveladas; }

    public boolean isHorasLiberadas() { return horasLiberadas; }
    public void setHorasLiberadas(boolean horasLiberadas) { this.horasLiberadas = horasLiberadas; }

    public String getSprint() { return sprint; }
    public void setSprint(String sprint) { this.sprint = sprint; }
}
