package com.planningapp.web.resource;

import com.planningapp.dto.AlterarPerfilRequest;
import com.planningapp.dto.RedefinirSenhaRequest;
import com.planningapp.dto.RegistrarUsuarioRequest;
import com.planningapp.dto.UsuarioGestaoDTO;
import com.planningapp.service.UsuarioService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Gestão de usuários (só SUPER, conferido no EJB). O original tinha os mesmos endpoints duplicados em
 * {@code /api/auth/usuarios} e {@code /api/usuarios}; aqui ficou só um.
 */
@Path("/usuarios")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UsuarioResource {

    @Inject
    private UsuarioService usuarioService;

    @GET
    public List<UsuarioGestaoDTO> listar() {
        return usuarioService.listar();
    }

    @POST
    public Response criar(@Valid @NotNull RegistrarUsuarioRequest request) {
        return Response.status(Response.Status.CREATED).entity(usuarioService.criar(request)).build();
    }

    @PUT
    @Path("/{usuario}/perfil")
    public UsuarioGestaoDTO alterarPerfil(@PathParam("usuario") String usuario, @Valid @NotNull AlterarPerfilRequest request) {
        return usuarioService.alterarPerfil(usuario, request.perfil());
    }

    @PUT
    @Path("/{usuario}/senha")
    public UsuarioGestaoDTO redefinirSenha(@PathParam("usuario") String usuario, @Valid @NotNull RedefinirSenhaRequest request) {
        return usuarioService.redefinirSenha(usuario, request.senha());
    }

    @POST
    @Path("/{usuario}/desativar")
    public UsuarioGestaoDTO desativar(@PathParam("usuario") String usuario) {
        return usuarioService.desativar(usuario);
    }

    @POST
    @Path("/{usuario}/reativar")
    public UsuarioGestaoDTO reativar(@PathParam("usuario") String usuario) {
        return usuarioService.reativar(usuario);
    }

    @DELETE
    @Path("/{usuario}")
    public Response excluir(@PathParam("usuario") String usuario) {
        usuarioService.excluir(usuario);
        return Response.noContent().build();
    }
}
