package com.planningapp.dto;

import java.time.Instant;

/**
 * Configuração do Redmine do moderador logado. A chave da API nunca volta para o cliente: só se há uma
 * gravada e os últimos caracteres, para conferência.
 *
 * @param configurada  {@code false} quando ainda não há configuração (os demais campos vêm nulos)
 * @param finalChave   últimos 4 caracteres da chave gravada
 */
public record ConfiguracaoRedmineDTO(boolean configurada, String url, String projeto, String finalChave,
                                     Instant atualizadaEm) {
}
