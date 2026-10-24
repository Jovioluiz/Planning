package com.planningapp.web.filter;

import com.planningapp.dto.ErroDTO;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.security.Principal;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uma linha de log por requisição da API, para acompanhar tudo no console do WildFly:
 * <pre>
 * INFO  POST /salas/14/tarefas/3/liberar -> 200 (35 ms) [ADM]
 * WARN  POST /salas/14/tarefas/3/votos/pontos -> 403 (12 ms) [ANA]: Você não participa desta tarefa
 * </pre>
 * Sucesso em INFO; erro do cliente (4xx) em WARNING, com a mensagem que o front recebeu; erro do servidor (5xx) em
 * SEVERE (a exceção, com o stack trace, o RESTEasy já registra logo antes). Os preflights do CORS (OPTIONS) ficam
 * de fora. O corpo das requisições não é registrado (tem senhas no login).
 */
@Provider
public class RegistroRequisicoes implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger("com.planningapp.requisicoes");
    private static final String INICIO = RegistroRequisicoes.class.getName() + ".inicio";

    @Override
    public void filter(ContainerRequestContext request) {
        request.setProperty(INICIO, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return;
        }
        int status = response.getStatus();
        Level nivel = status >= 500 ? Level.SEVERE : status >= 400 ? Level.WARNING : Level.INFO;
        if (!LOG.isLoggable(nivel)) {
            return;
        }

        StringBuilder linha = new StringBuilder()
                .append(request.getMethod()).append(' ').append(caminho(request))
                .append(" -> ").append(status);
        Object inicio = request.getProperty(INICIO);
        if (inicio instanceof Long nanos) {
            linha.append(" (").append((System.nanoTime() - nanos) / 1_000_000).append(" ms)");
        }
        Principal usuario = request.getSecurityContext().getUserPrincipal();
        linha.append(" [").append(usuario != null ? usuario.getName() : "anônimo").append(']');
        if (response.getEntity() instanceof ErroDTO erro) {
            linha.append(": ").append(erro.mensagem());
            if (erro.detalhes() != null && !erro.detalhes().isEmpty()) {
                linha.append(' ').append(erro.detalhes());
            }
        }
        LOG.log(nivel, linha::toString);
    }

    private static String caminho(ContainerRequestContext request) {
        String caminho = request.getUriInfo().getPath();
        if (!caminho.startsWith("/")) {
            caminho = "/" + caminho;
        }
        String query = request.getUriInfo().getRequestUri().getRawQuery();
        return query == null ? caminho : caminho + "?" + query;
    }
}
