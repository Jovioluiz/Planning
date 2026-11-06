package com.planningapp.dto;

/** Bytes de uma foto de perfil e o media type, para o recurso devolver como imagem. */
public record FotoDTO(byte[] conteudo, String tipo) {
}
