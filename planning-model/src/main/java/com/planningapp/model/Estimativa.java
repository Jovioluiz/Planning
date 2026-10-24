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

/** Voto de um usuário numa tarefa, numa rodada. Os votos de rodadas anteriores ficam como histórico. */
@Entity
@Table(name = "estimativas",
        uniqueConstraints = @UniqueConstraint(name = "uk_estimativa_tarefa_usuario_rodada",
                columnNames = {"id_tarefa", "id_usuario", "rodada"}))
public class Estimativa {

    /** A "carta café" (passar a vez) é gravada como 0, igual ao Planning original. */
    public static final int CAFE = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tarefa", nullable = false)
    private Tarefa tarefa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private int rodada;

    private Integer pontos;

    private Integer horas;

    /** Só para usuários TESTE, que não votam em pontos nem horas de desenvolvimento. */
    @Column(name = "horas_teste")
    private Integer horasTeste;

    @Column(name = "votado_em_pontos")
    private Instant votadoEmPontos;

    @Column(name = "votado_em_horas")
    private Instant votadoEmHoras;

    @Column(name = "votado_em_horas_teste")
    private Instant votadoEmHorasTeste;

    protected Estimativa() {
        // exigido pelo JPA
    }

    public Estimativa(Tarefa tarefa, Usuario usuario, int rodada) {
        this.tarefa = tarefa;
        this.usuario = usuario;
        this.rodada = rodada;
    }

    public void votarPontos(int pontos) {
        this.pontos = pontos;
        this.votadoEmPontos = Instant.now();
    }

    public void votarHoras(int horas) {
        this.horas = horas;
        this.votadoEmHoras = Instant.now();
    }

    public void votarHorasTeste(int horasTeste) {
        this.horasTeste = horasTeste;
        this.votadoEmHorasTeste = Instant.now();
    }

    public boolean votouPontos() {
        return pontos != null;
    }

    public boolean votouHoras() {
        return horas != null;
    }

    public boolean votouHorasTeste() {
        return horasTeste != null;
    }

    public boolean isCafe() {
        return pontos != null && pontos == CAFE;
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

    public int getRodada() {
        return rodada;
    }

    public Integer getPontos() {
        return pontos;
    }

    public Integer getHoras() {
        return horas;
    }

    public Instant getVotadoEmPontos() {
        return votadoEmPontos;
    }

    public Instant getVotadoEmHoras() {
        return votadoEmHoras;
    }

    public Integer getHorasTeste() {
        return horasTeste;
    }

    public Instant getVotadoEmHorasTeste() {
        return votadoEmHorasTeste;
    }
}
