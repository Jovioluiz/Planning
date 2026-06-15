package com.planningapp.entity;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonRawValue;
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

    private boolean pontosRevelados = false;
    private boolean horasReveladas = false;
    private boolean horasLiberadas = false;
    private boolean horasTesteLiberadas = false;
    private boolean horasTesteReveladas = false;
    private String sprint;
    private Instant liberadaEm;
    private Instant horasLiberadasEm;
    private Instant horasTesteLiberadasEm;
    private Instant estimadaEm;
    private Integer rodadaAtual = 1;

    @Column(columnDefinition = "boolean default false")
    private boolean pulada = false;

    @Column(columnDefinition = "TEXT")
    private String dadosExtras;

    @ManyToOne
    @JoinColumn(name = "id_sala", nullable = true)
    private Sala sala;

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

    public Instant getLiberadaEm() { return liberadaEm; }
    public void setLiberadaEm(Instant liberadaEm) { this.liberadaEm = liberadaEm; }

    public Instant getHorasLiberadasEm() { return horasLiberadasEm; }
    public void setHorasLiberadasEm(Instant horasLiberadasEm) { this.horasLiberadasEm = horasLiberadasEm; }

    public boolean isHorasTesteLiberadas() { return horasTesteLiberadas; }
    public void setHorasTesteLiberadas(boolean horasTesteLiberadas) { this.horasTesteLiberadas = horasTesteLiberadas; }

    public boolean isHorasTesteReveladas() { return horasTesteReveladas; }
    public void setHorasTesteReveladas(boolean horasTesteReveladas) { this.horasTesteReveladas = horasTesteReveladas; }

    public Instant getHorasTesteLiberadasEm() { return horasTesteLiberadasEm; }
    public void setHorasTesteLiberadasEm(Instant horasTesteLiberadasEm) { this.horasTesteLiberadasEm = horasTesteLiberadasEm; }

    public Instant getEstimadaEm() { return estimadaEm; }
    public void setEstimadaEm(Instant estimadaEm) { this.estimadaEm = estimadaEm; }

    public Integer getRodadaAtual() { return rodadaAtual != null ? rodadaAtual : 1; }
    public void setRodadaAtual(Integer rodadaAtual) { this.rodadaAtual = rodadaAtual; }

    public boolean isPulada() { return pulada; }
    public void setPulada(boolean pulada) { this.pulada = pulada; }

    @JsonRawValue
    public String getDadosExtras() { return dadosExtras; }
    public void setDadosExtras(String dadosExtras) { this.dadosExtras = dadosExtras; }

    public Sala getSala() { return sala; }
    public void setSala(Sala sala) { this.sala = sala; }
}
