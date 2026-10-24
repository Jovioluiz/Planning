package com.planningapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Usuário que entrou na sala pelo código. JOGADORes membros viram participantes das tarefas da sala. */
@Entity
@Table(name = "sala_membros",
        uniqueConstraints = @UniqueConstraint(name = "uk_sala_membro", columnNames = {"id_sala", "id_usuario"}))
public class MembroSala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sala", nullable = false)
    private Sala sala;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "entrou_em", nullable = false)
    private Instant entrouEm;

    protected MembroSala() {
        // exigido pelo JPA
    }

    public MembroSala(Sala sala, Usuario usuario) {
        this.sala = sala;
        this.usuario = usuario;
    }

    @PrePersist
    void aoCriar() {
        entrouEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Sala getSala() {
        return sala;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Instant getEntrouEm() {
        return entrouEm;
    }
}
