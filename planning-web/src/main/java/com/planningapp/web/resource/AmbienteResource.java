package com.planningapp.web.resource;

import com.planningapp.dto.AmbienteDTO;
import com.planningapp.service.Ambiente;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Público: em que ambiente (banco) o servidor está. O front mostra uma faixa quando não é produção. */
@Path("/ambiente")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class AmbienteResource {

    @GET
    public AmbienteDTO ambiente() {
        return new AmbienteDTO(Ambiente.atual(), Ambiente.producao());
    }
}
