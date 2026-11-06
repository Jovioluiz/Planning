package com.planningapp.web.resource;

import com.planningapp.service.PerfilService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Response;

/**
 * Foto de perfil de um usuário, para os avatares (qualquer usuário logado vê; conferido no EJB). Sem foto, 204:
 * o front mostra a inicial do nome, sem um 404 no console para cada participante.
 */
@Path("/fotos/{usuario}")
@RequestScoped
public class FotoResource {

    @Inject
    private PerfilService perfilService;

    @GET
    public Response foto(@PathParam("usuario") String usuario) {
        CacheControl cache = new CacheControl();
        cache.setPrivate(true);
        cache.setMaxAge(300);
        return perfilService.foto(usuario)
                .map(f -> Response.ok(f.conteudo(), f.tipo())
                        .cacheControl(cache)
                        .header("X-Content-Type-Options", "nosniff"))
                .orElseGet(Response::noContent)
                .build();
    }
}
