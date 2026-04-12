package com.planningapp.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "task_participants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "participante"}))
public class TaskParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private String participante;

    public TaskParticipant() {}

    public TaskParticipant(Long taskId, String participante) {
        this.taskId = taskId;
        this.participante = participante;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public String getParticipante() { return participante; }
}
