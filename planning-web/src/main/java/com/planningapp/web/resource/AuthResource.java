package com.planningapp.web.resource;

import com.planningapp.dto.LoginRequest;
import com.planningapp.dto.RegistrarUsuarioRequest;
import com.planningapp.dto.TokenDTO;
import com.planningapp.dto.UsuarioDTO;
import com.planningapp.service.AuthService;
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

    @GET
    @Path("/me")
    public UsuarioDTO me() {
        return authService.usuarioLogado();
    }
}
