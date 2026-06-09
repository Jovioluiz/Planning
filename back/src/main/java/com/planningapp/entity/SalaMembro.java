package com.planningapp.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sala_membros",
       uniqueConstraints = @UniqueConstraint(columnNames = {"id_sala", "id_usuario"}))
public class SalaMembro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_sala", nullable = false)
    private Sala sala;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private User usuario;

    public SalaMembro() {}

    public SalaMembro(Sala sala, User usuario) {
        this.sala = sala;
        this.usuario = usuario;
    }

    public Long getId() { return id; }

    public Sala getSala() { return sala; }
    public void setSala(Sala sala) { this.sala = sala; }

    public User getUsuario() { return usuario; }
    public void setUsuario(User usuario) { this.usuario = usuario; }
}
