package com.planningapp.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "estimativas")
public class Estimation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_tarefas", insertable = false, updatable = false)
    private Long taskId;

    private String participante;
    private Integer pontos;

    @Column(name = "revelada")
    private boolean revealed = false;

    private Double horas;

    @Column(name = "horas_reveladas")
    private Boolean horasReveladas = false;

    @ManyToOne
    @JoinColumn(name = "id_tarefas", nullable = false)
    private Task task;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getParticipante() { return participante; }
    public void setParticipante(String participante) { this.participante = participante; }

    public Integer getPontos() { return pontos; }
    public void setPontos(Integer pontos) { this.pontos = pontos; }

    public boolean isRevealed() { return revealed; }
    public void setRevealed(boolean revealed) { this.revealed = revealed; }

    // CORRIGIDO: retorna null em vez de 0.0 quando não votou —
    // assim todosVotaramHoras() consegue distinguir "não votou" de "votou 0 horas".
    public Double getHoras() { return horas; }
    public void setHoras(Double horas) { this.horas = horas; }

    public void setTarefa(Task task) { this.task = task; }

    public Boolean isHorasReveladas() { return horasReveladas; }
    public void setHorasReveladas(boolean horasReveladas) { this.horasReveladas = horasReveladas; }
}
