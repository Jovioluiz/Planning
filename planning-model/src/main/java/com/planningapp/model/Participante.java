package com.planningapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * JOGADOR que pode votar numa tarefa (o {@code TaskParticipant} do Planning original).
 *
 * <p>A remoção é lógica ({@code removidoEm}): a linha continua existindo para que a pessoa não seja
 * vinculada de novo sem querer e para a lista mostrar quem foi removido. O moderador pode readmitir.</p>
 */
@Entity
@Table(name = "participantes_tarefa",
        uniqueConstraints = @UniqueConstraint(name = "uk_participante_tarefa", columnNames = {"id_tarefa", "id_usuario"}))
public class Participante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tarefa", nullable = false)
    private Tarefa tarefa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "removido_em")
    private Instant removidoEm;

    protected Participante() {
        // exigido pelo JPA
    }

    public Participante(Tarefa tarefa, Usuario usuario) {
        this.tarefa = tarefa;
        this.usuario = usuario;
    }

    public void remover() {
        removidoEm = Instant.now();
    }

    public void readmitir() {
        removidoEm = null;
    }

    public boolean isAtivo() {
        return removidoEm == null;
    }

    public Long getId() {
        return id;
    }

    public Tarefa getTarefa() {
        return tarefa;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Instant getRemovidoEm() {
        return removidoEm;
    }
}
