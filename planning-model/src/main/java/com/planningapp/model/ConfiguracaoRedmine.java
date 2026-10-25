package com.planningapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Acesso ao Redmine de um moderador e a consulta cujos chamados ele importa. Uma por usuário: a chave da API é
 * pessoal e o Redmine aplica as permissões de quem a gerou.
 */
@Entity
@Table(name = "configuracoes_redmine")
public class ConfiguracaoRedmine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false, unique = true)
    private Usuario usuario;

    /**
     * Link da lista de chamados a importar, como aparece no navegador
     * (ex.: {@code https://redmine.empresa.com.br/projects/erp/issues?query_id=123}).
     */
    @Column(nullable = false, length = 4000)
    private String url;

    /** Identificador do projeto, usado quando o link da consulta não é de um projeto. */
    @Column(length = 100)
    private String projeto;

    @Column(name = "chave_api", nullable = false, length = 100)
    private String chaveApi;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    protected ConfiguracaoRedmine() {
        // exigido pelo JPA
    }

    public ConfiguracaoRedmine(Usuario usuario) {
        this.usuario = usuario;
    }

    public void alterar(String url, String projeto, String chaveApi) {
        this.url = url;
        this.projeto = projeto;
        this.chaveApi = chaveApi;
        this.atualizadaEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getUrl() {
        return url;
    }

    public String getProjeto() {
        return projeto;
    }

    public String getChaveApi() {
        return chaveApi;
    }

    public Instant getAtualizadaEm() {
        return atualizadaEm;
    }
}
