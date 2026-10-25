package com.planningapp.dto;

/**
 * Resultado do teste de conexão com o Redmine.
 *
 * @param usuario  nome de quem gerou a chave da API
 * @param projeto  nome do projeto configurado; {@code null} quando não há projeto na configuração
 * @param chamados quantos chamados a consulta configurada traz hoje
 */
public record TesteRedmineDTO(String usuario, String projeto, int chamados) {
}
