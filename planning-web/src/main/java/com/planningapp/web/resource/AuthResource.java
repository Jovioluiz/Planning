package com.planningapp.web.resource;

import com.planningapp.dto.EsqueciSenhaRequest;
import com.planningapp.dto.LoginRequest;
import com.planningapp.dto.RedefinirSenhaPorLinkRequest;
import com.planningapp.dto.RegistrarUsuarioRequest;
import com.planningapp.dto.TokenDTO;
import com.planningapp.dto.UsuarioDTO;
import com.planningapp.service.AuthService;
import com.planningapp.service.RedefinicaoSenhaService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    private AuthService authService;

    @Inject
    private RedefinicaoSenhaService redefinicaoSenhaService;

    @POST
    @Path("/registrar")
    public Response registrar(@Valid @NotNull RegistrarUsuarioRequest request) {
        return Response.status(Response.Status.CREATED)
                .entity(authService.registrar(request))
                .build();
    }

    @POST
    @Path("/login")
    public TokenDTO login(@Valid @NotNull LoginRequest request) {
        return authService.login(request);
    }

    /** Sempre 202, exista ou não o usuário (não revela quem está cadastrado); o link vai por e-mail. */
    @POST
    @Path("/esqueci-senha")
    public Response esqueciSenha(@Valid @NotNull EsqueciSenhaRequest request) {
        redefinicaoSenhaService.solicitar(request);
        return Response.accepted().build();
    }

    @POST
    @Path("/redefinir-senha")
    public UsuarioDTO redefinirSenha(@Valid @NotNull RedefinirSenhaPorLinkRequest request) {
        return redefinicaoSenhaService.redefinir(request);
    }

    @GET
    @Path("/me")
    public UsuarioDTO me() {
        return authService.usuarioLogado();
    }
}
