package com.planningapp.dto;

import com.planningapp.entity.Sala;

import java.time.Instant;
import java.util.List;

public class SalaDTO {

    private Long id;
    private String nome;
    private String codigo;
    private String moderador;
    private boolean ativa;
    private Instant criadaEm;
    private List<String> membros;

    public SalaDTO() {}

    public static SalaDTO from(Sala sala, List<String> membros) {
        SalaDTO dto = new SalaDTO();
        dto.id = sala.getId();
        dto.nome = sala.getNome();
        dto.codigo = sala.getCodigo();
        dto.moderador = sala.getModerador().getUsuario();
        dto.ativa = sala.isAtiva();
        dto.criadaEm = sala.getCriadaEm();
        dto.membros = membros;
        return dto;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getCodigo() { return codigo; }
    public String getModerador() { return moderador; }
    public boolean isAtiva() { return ativa; }
    public Instant getCriadaEm() { return criadaEm; }
    public List<String> getMembros() { return membros; }
}
