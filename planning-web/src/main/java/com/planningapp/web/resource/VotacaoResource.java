package com.planningapp.web.resource;

import com.planningapp.dto.VotacaoDTO;
import com.planningapp.dto.VotoHorasRequest;
import com.planningapp.dto.VotoPontosRequest;
import com.planningapp.service.VotacaoService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Todas as operações devolvem a situação atualizada da votação, já com a visibilidade de quem chamou. */
@Path("/salas/{salaId}/tarefas/{tarefaId}/votacao")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VotacaoResource {

    @Inject
    private VotacaoService votacaoService;

    @PathParam("salaId")
    private Long salaId;

    @PathParam("tarefaId")
    private Long tarefaId;

    @GET
    public VotacaoDTO consultar() {
        return votacaoService.consultar(salaId, tarefaId);
    }

    @POST
    @Path("/pontos")
    public VotacaoDTO votarPontos(@Valid @NotNull VotoPontosRequest request) {
        return votacaoService.votarPontos(salaId, tarefaId, request.pontos());
    }

    @POST
    @Path("/horas")
    public VotacaoDTO votarHoras(@Valid @NotNull VotoHorasRequest request) {
        return votacaoService.votarHoras(salaId, tarefaId, request.horas());
    }

    @POST
    @Path("/revelar-pontos")
    public VotacaoDTO revelarPontos() {
        return votacaoService.revelarPontos(salaId, tarefaId);
    }

    @POST
    @Path("/liberar-horas")
    public VotacaoDTO liberarHoras() {
        return votacaoService.liberarHoras(salaId, tarefaId);
    }

    @POST
    @Path("/revelar-horas")
    public VotacaoDTO revelarHoras() {
        return votacaoService.revelarHoras(salaId, tarefaId);
    }

    @POST
    @Path("/horas-teste")
    public VotacaoDTO votarHorasTeste(@Valid @NotNull VotoHorasRequest request) {
        return votacaoService.votarHorasTeste(salaId, tarefaId, request.horas());
    }

    @POST
    @Path("/liberar-horas-teste")
    public VotacaoDTO liberarHorasTeste() {
        return votacaoService.liberarHorasTeste(salaId, tarefaId);
    }

    @POST
    @Path("/revelar-horas-teste")
    public VotacaoDTO revelarHorasTeste() {
        return votacaoService.revelarHorasTeste(salaId, tarefaId);
    }

    @POST
    @Path("/nova-rodada")
    public VotacaoDTO novaRodada() {
        return votacaoService.novaRodada(salaId, tarefaId);
    }
}
