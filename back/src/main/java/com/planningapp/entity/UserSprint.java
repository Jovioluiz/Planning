package com.planningapp.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_sprints", uniqueConstraints = @UniqueConstraint(columnNames = {"id_usuario", "sprint"}))
public class UserSprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private User usuario;

    private String sprint;

    public UserSprint() {}

    public UserSprint(User usuario, String sprint) {
        this.usuario = usuario;
        this.sprint = sprint;
    }

    public Long getId() { return id; }
    public User getUsuario() { return usuario; }
    public void setUsuario(User usuario) { this.usuario = usuario; }
    public String getSprint() { return sprint; }
    public void setSprint(String sprint) { this.sprint = sprint; }
}
