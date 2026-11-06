package com.planningapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String usuario;

    /** Hash PBKDF2 no formato do {@code Pbkdf2PasswordHash} do Jakarta Security. Nunca a senha em texto. */
    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    /** Desativado não faz login e o token que já tinha para de valer. */
    @Column(nullable = false)
    private boolean ativo = true;

    /** Opcional, em minúsculas. Para onde vai o link do "Esqueci minha senha". */
    @Column(length = 150)
    private String email;

    /** SHA-256 (hex) do token do link de redefinição de senha em aberto; nulo se não há pedido. */
    @Column(name = "redefinicao_hash", length = 64)
    private String redefinicaoHash;

    @Column(name = "redefinicao_expira_em")
    private Instant redefinicaoExpiraEm;

    protected Usuario() {
        // exigido pelo JPA
    }

    public Usuario(String usuario, String senhaHash, Perfil perfil) {
        this.usuario = usuario;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
    }

    @PrePersist
    void aoPersistir() {
        criadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void alterarPerfil(Perfil novoPerfil) {
        perfil = novoPerfil;
    }

    public void alterarSenha(String novoHash) {
        senhaHash = novoHash;
    }

    public String getEmail() {
        return email;
    }

    public void alterarEmail(String novoEmail) {
        email = novoEmail;
    }

    /** Guarda o pedido de redefinição de senha (substitui um anterior, que deixa de valer). */
    public void abrirRedefinicaoSenha(String hashToken, Instant expiraEm) {
        redefinicaoHash = hashToken;
        redefinicaoExpiraEm = expiraEm;
    }

    public void fecharRedefinicaoSenha() {
        redefinicaoHash = null;
        redefinicaoExpiraEm = null;
    }

    public String getRedefinicaoHash() {
        return redefinicaoHash;
    }

    public Instant getRedefinicaoExpiraEm() {
        return redefinicaoExpiraEm;
    }

    public void desativar() {
        ativo = false;
    }

    public void reativar() {
        ativo = true;
    }
}
