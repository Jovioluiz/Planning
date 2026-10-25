package com.planningapp.web.resource;

import com.planningapp.dto.ConfiguracaoRedmineDTO;
import com.planningapp.dto.SalvarConfiguracaoRedmineRequest;
import com.planningapp.dto.TesteRedmineDTO;
import com.planningapp.service.RedmineService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Acesso ao Redmine do moderador logado (ADMIN). A importação em si fica em {@code /salas/{id}/tarefas/importar-redmine}. */
@Path("/redmine/configuracao")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RedmineResource {

    @Inject
    private RedmineService redmineService;

    @GET
    public ConfiguracaoRedmineDTO buscar() {
        return redmineService.buscarConfiguracao();
    }

    @PUT
    public ConfiguracaoRedmineDTO salvar(@Valid @NotNull SalvarConfiguracaoRedmineRequest request) {
        return redmineService.salvarConfiguracao(request);
    }

    @POST
    @Path("/testar")
    public TesteRedmineDTO testar() {
        return redmineService.testarConexao();
    }
}
