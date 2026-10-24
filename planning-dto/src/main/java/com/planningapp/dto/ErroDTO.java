package com.planningapp.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ErroDTO(int status, String mensagem, List<String> detalhes, LocalDateTime em) {

    public ErroDTO(int status, String mensagem) {
        this(status, mensagem, List.of(), LocalDateTime.now());
    }

    public ErroDTO(int status, String mensagem, List<String> detalhes) {
        this(status, mensagem, detalhes, LocalDateTime.now());
    }
}
