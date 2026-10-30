package com.planningapp.web.resource;

import com.planningapp.dto.CriarTarefaRequest;
import com.planningapp.dto.EnvioRedmineDTO;
import com.planningapp.dto.FinalizarTarefaRequest;
import com.planningapp.dto.ResultadoImportacaoDTO;
import com.planningapp.dto.TarefaDTO;
import com.planningapp.service.RedmineService;
import com.planningapp.service.TarefaService;
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

import java.util.List;

@Path("/salas/{salaId}/tarefas")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TarefaResource {

    @Inject
    private TarefaService tarefaService;

    @GET
    public List<TarefaDTO> listar(@PathParam("salaId") Long salaId) {
        return tarefaService.listarPorSala(salaId);
    }

    @POST
    public Response criar(@PathParam("salaId") Long salaId, @Valid @NotNull CriarTarefaRequest request) {
        return Response.status(Response.Status.CREATED)
                .entity(tarefaService.criar(salaId, request))
                .build();
    }

    /**
     * Mesmo path, dois formatos: o JAX-RS escolhe o método pelo {@code Content-Type} da requisição.
     * A validação é feita no service (e não com {@code @Valid}) para os erros citarem a linha.
     */
    @POST
    @Path("/importar")
    public ResultadoImportacaoDTO importar(@PathParam("salaId") Long salaId, @NotNull List<CriarTarefaRequest> itens) {
        return tarefaService.importar(salaId, itens);
    }

    @POST
    @Path("/importar")
    @Consumes({"text/csv", MediaType.TEXT_PLAIN})
    public ResultadoImportacaoDTO importarCsv(@PathParam("salaId") Long salaId, @NotNull String csv) {
        return tarefaService.importarCsv(salaId, csv);
    }

    /** Os chamados da consulta do Redmine configurada pelo moderador ({@code /redmine/configuracao}). Sem corpo. */
    @POST
    @Path("/importar-redmine")
    public ResultadoImportacaoDTO importarRedmine(@PathParam("salaId") Long salaId) {
        return tarefaService.importarRedmine(salaId);
    }

    @GET
    @Path("/{id}")
    public TarefaDTO buscar(@PathParam("salaId") Long salaId, @PathParam("id") Long id) {
        return tarefaService.buscar(salaId, id);
    }

    @POST
    @Path("/{id}/liberar")
    public TarefaDTO liberar(@PathParam("salaId") Long salaId, @PathParam("id") Long id) {
        return tarefaService.liberar(salaId, id);
    }

    @POST
    @Path("/{id}/finalizar")
    public TarefaDTO finalizar(@PathParam("salaId") Long salaId, @PathParam("id") Long id,
                               @Valid @NotNull FinalizarTarefaRequest request) {
        return tarefaService.finalizar(salaId, id, request.pontosFinais(), request.horasFinais(),
                request.horasTesteFinais());
    }

    /** Grava a estimativa final no chamado do Redmine. Sem corpo; só para tarefas importadas do Redmine. */
    @POST
    @Path("/{id}/enviar-redmine")
    public EnvioRedmineDTO enviarAoRedmine(@PathParam("salaId") Long salaId, @PathParam("id") Long id) {
        return tarefaService.enviarAoRedmine(salaId, id);
    }

    /** Relê o chamado no Redmine e atualiza a tarefa (título, descrição e campos). Sem corpo. */
    @POST
    @Path("/{id}/atualizar-redmine")
    public TarefaDTO atualizarDoRedmine(@PathParam("salaId") Long salaId, @PathParam("id") Long id) {
        return tarefaService.atualizarDoRedmine(salaId, id);
    }

    /** Imagem anexada ao chamado (a descrição cita pelo nome: {@code !print.png!}), baixada com a chave do moderador. */
    @GET
    @Path("/{id}/anexos-redmine/{nome}")
    @Produces({"image/*", MediaType.APPLICATION_JSON})
    public Response anexoRedmine(@PathParam("salaId") Long salaId, @PathParam("id") Long id,
                                 @PathParam("nome") String nome) {
        RedmineService.Arquivo arquivo = tarefaService.baixarAnexoRedmine(salaId, id, nome);
        return Response.ok(arquivo.conteudo(), arquivo.tipo())
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, max-age=300")
                .build();
    }

    @POST
    @Path("/{id}/pular")
    public TarefaDTO pular(@PathParam("salaId") Long salaId, @PathParam("id") Long id) {
        return tarefaService.pular(salaId, id);
    }

    @DELETE
    @Path("/{id}")
    public Response excluir(@PathParam("salaId") Long salaId, @PathParam("id") Long id) {
        tarefaService.excluir(salaId, id);
        return Response.noContent().build();
    }
}
