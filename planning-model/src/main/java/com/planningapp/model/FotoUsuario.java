package com.planningapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Foto de perfil de um usuário (no máximo uma). Fica fora de {@link Usuario} para a consulta do usuário, feita a
 * cada requisição, não carregar a imagem. O front já manda a foto recortada e reduzida (256x256, JPEG).
 */
@Entity
@Table(name = "fotos_usuario")
public class FotoUsuario {

    /** O id do usuário (chave primária e estrangeira). */
    @Id
    @Column(name = "id_usuario")
    private Long usuarioId;

    @Column(nullable = false, length = 1_000_000)
    private byte[] conteudo;

    /** Media type: image/jpeg, image/png ou image/webp. */
    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    protected FotoUsuario() {
        // exigido pelo JPA
    }

    public FotoUsuario(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public void alterar(byte[] conteudo, String tipo) {
        this.conteudo = conteudo;
        this.tipo = tipo;
        this.atualizadaEm = Instant.now();
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public byte[] getConteudo() {
        return conteudo;
    }

    public String getTipo() {
        return tipo;
    }

    public Instant getAtualizadaEm() {
        return atualizadaEm;
    }
}
