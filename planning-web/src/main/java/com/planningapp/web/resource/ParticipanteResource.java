package com.planningapp.web.resource;

import com.planningapp.dto.ParticipanteDTO;
import com.planningapp.service.ParticipanteService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Remover e adicionar devolvem a lista atualizada. */
@Path("/salas/{salaId}/tarefas/{tarefaId}/participantes")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ParticipanteResource {

    @Inject
    private ParticipanteService participanteService;

    @PathParam("salaId")
    private Long salaId;

    @PathParam("tarefaId")
    private Long tarefaId;

    @GET
    public List<ParticipanteDTO> listar() {
        return participanteService.listar(salaId, tarefaId);
    }

    @POST
    @Path("/{usuario}")
    public List<ParticipanteDTO> adicionar(@PathParam("usuario") String usuario) {
        return participanteService.adicionar(salaId, tarefaId, usuario);
    }

    @DELETE
    @Path("/{usuario}")
    public List<ParticipanteDTO> remover(@PathParam("usuario") String usuario) {
        return participanteService.remover(salaId, tarefaId, usuario);
    }
}
