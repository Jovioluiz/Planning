package com.planningapp.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "estimativas")
public class Estimation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_tarefas", insertable = false, updatable = false)
    private Long taskId;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private User usuario;

    private Integer pontos;

    @Column(name = "revelada")
    private boolean revealed = false;

    private Double horas;

    @Column(name = "horas_reveladas")
    private Boolean horasReveladas = false;

    @Column(name = "rodada")
    private Integer rodada = 1;

    @Column(name = "votado_em_pontos")
    private Instant votadoEmPontos;

    @Column(name = "votado_em_horas")
    private Instant votadoEmHoras;

    @ManyToOne
    @JoinColumn(name = "id_tarefas", nullable = false)
    private Task task;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public User getUsuario() { return usuario; }
    public void setUsuario(User usuario) { this.usuario = usuario; }

    public Integer getPontos() { return pontos; }
    public void setPontos(Integer pontos) { this.pontos = pontos; }

    public boolean isRevealed() { return revealed; }
    public void setRevealed(boolean revealed) { this.revealed = revealed; }

    public Double getHoras() { return horas; }
    public void setHoras(Double horas) { this.horas = horas; }

    public void setTarefa(Task task) { this.task = task; }

    public Boolean isHorasReveladas() { return horasReveladas; }
    public void setHorasReveladas(boolean horasReveladas) { this.horasReveladas = horasReveladas; }

    public Integer getRodada() { return rodada != null ? rodada : 1; }
    public void setRodada(Integer rodada) { this.rodada = rodada; }

    public Instant getVotadoEmPontos() { return votadoEmPontos; }
    public void setVotadoEmPontos(Instant votadoEmPontos) { this.votadoEmPontos = votadoEmPontos; }

    public Instant getVotadoEmHoras() { return votadoEmHoras; }
    public void setVotadoEmHoras(Instant votadoEmHoras) { this.votadoEmHoras = votadoEmHoras; }
}
