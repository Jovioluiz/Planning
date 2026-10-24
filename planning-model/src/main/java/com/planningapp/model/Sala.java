package com.planningapp.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "salas")
public class Sala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 36)
    private String codigo;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_moderador", nullable = false)
    private Usuario moderador;

    @OneToMany(mappedBy = "sala", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero")
    private List<Tarefa> tarefas = new ArrayList<>();

    protected Sala() {
        // exigido pelo JPA
    }

    public Sala(String nome, Usuario moderador) {
        this.nome = nome;
        this.moderador = moderador;
    }

    public boolean isModeradaPor(String usuario) {
        return moderador != null && moderador.getUsuario().equals(usuario);
    }

    @PrePersist
    void aoPersistir() {
        if (codigo == null) {
            codigo = UUID.randomUUID().toString();
        }
        criadaEm = LocalDateTime.now();
    }

    public void adicionarTarefa(Tarefa tarefa) {
        tarefa.setSala(this);
        tarefas.add(tarefa);
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCodigo() {
        return codigo;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public Usuario getModerador() {
        return moderador;
    }

    public List<Tarefa> getTarefas() {
        return tarefas;
    }
}
