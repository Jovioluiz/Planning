package com.planningapp.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "task_participants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "id_usuario"}))
public class TaskParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private User usuario;

    public TaskParticipant() {}

    public TaskParticipant(Long taskId, User usuario) {
        this.taskId = taskId;
        this.usuario = usuario;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public User getUsuario() { return usuario; }
}
