package com.planningapp.web.resource;

import com.planningapp.dto.CriarSalaRequest;
import com.planningapp.dto.SalaDTO;
import com.planningapp.dto.SalaModeradaDTO;
import com.planningapp.service.SalaService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;

import java.util.List;

@Path("/salas")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SalaResource {

    @Inject
    private SalaService salaService;

    @Context
    private UriInfo uriInfo;

    @GET
    public List<SalaDTO> listar() {
        return salaService.listarAtivas();
    }

    @GET
    @Path("/codigo/{codigo}")
    public SalaDTO buscarPorCodigo(@PathParam("codigo") String codigo) {
        return salaService.buscarPorCodigo(codigo);
    }

    /** Precisa ser chamado antes de votar: é o que vincula o JOGADOR às tarefas da sala. */
    @POST
    @Path("/codigo/{codigo}/entrar")
    public SalaDTO entrar(@PathParam("codigo") String codigo) {
        return salaService.entrar(codigo);
    }

    @POST
    public Response criar(@Valid @NotNull CriarSalaRequest request) {
        SalaDTO sala = salaService.criar(request);
        return Response.created(uriInfo.getAbsolutePathBuilder().path("codigo").path(sala.codigo()).build())
                .entity(sala)
                .build();
    }

    @DELETE
    @Path("/{salaId}")
    public Response desativar(@PathParam("salaId") Long salaId) {
        salaService.desativar(salaId);
        return Response.noContent().build();
    }

    /** Salas do moderador logado, ativas e inativas, com os membros. */
    @GET
    @Path("/minhas")
    public List<SalaModeradaDTO> minhas() {
        return salaService.minhas();
    }

    @POST
    @Path("/{salaId}/reativar")
    public SalaModeradaDTO reativar(@PathParam("salaId") Long salaId) {
        return salaService.reativar(salaId);
    }

    /** Quem está conectado no WebSocket da sala (a lista "Online agora" do front). */
    @GET
    @Path("/{salaId}/online")
    public List<String> online(@PathParam("salaId") Long salaId) {
        return salaService.online(salaId);
    }
}
