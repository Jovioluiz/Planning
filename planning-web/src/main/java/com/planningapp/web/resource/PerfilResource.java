package com.planningapp.web.resource;

import com.planningapp.dto.AlterarEmailRequest;
import com.planningapp.dto.AlterarSenhaRequest;
import com.planningapp.dto.PerfilDTO;
import com.planningapp.service.PerfilService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** "Meu perfil" do usuário logado: senha, e-mail e foto (as regras ficam no {@link PerfilService}). */
@Path("/perfil")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PerfilResource {

    @Inject
    private PerfilService perfilService;

    @GET
    public PerfilDTO meuPerfil() {
        return perfilService.meuPerfil();
    }

    @PUT
    @Path("/senha")
    public Response alterarSenha(@Valid @NotNull AlterarSenhaRequest request) {
        perfilService.alterarSenha(request);
        return Response.noContent().build();
    }

    @PUT
    @Path("/email")
    public PerfilDTO alterarEmail(@Valid @NotNull AlterarEmailRequest request) {
        return perfilService.alterarEmail(request);
    }

    /** O corpo é a própria imagem (o tipo é conferido pelo conteúdo, no service). */
    @PUT
    @Path("/foto")
    @Consumes({"image/jpeg", "image/png", "image/webp", MediaType.APPLICATION_OCTET_STREAM})
    public PerfilDTO salvarFoto(byte[] conteudo) {
        return perfilService.salvarFoto(conteudo);
    }

    @DELETE
    @Path("/foto")
    public PerfilDTO removerFoto() {
        return perfilService.removerFoto();
    }
}
