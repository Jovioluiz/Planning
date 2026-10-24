package com.planningapp.web.resource;

import com.planningapp.dto.EstatisticasDTO;
import com.planningapp.service.EstatisticasService;
import com.planningapp.service.exception.DadosInvalidosException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** {@code GET /api/estatisticas?salaId=&sprint=&de=2026-10-01&ate=2026-10-31}, todos opcionais. */
@Path("/estatisticas")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class EstatisticasResource {

    @Inject
    private EstatisticasService estatisticasService;

    /**
     * As datas chegam como texto: o JAX-RS só converte sozinho tipos com {@code valueOf}/{@code fromString}
     * ou construtor de String, e {@code LocalDate} tem {@code parse}. Assim o erro também sai no formato padrão.
     */
    @GET
    public EstatisticasDTO calcular(@QueryParam("salaId") Long salaId, @QueryParam("sprint") String sprint,
                                    @QueryParam("de") String de, @QueryParam("ate") String ate) {
        return estatisticasService.calcular(salaId, sprint, data("de", de), data("ate", ate));
    }

    private static LocalDate data(String nome, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new DadosInvalidosException("Parâmetro inválido",
                    List.of("'" + nome + "' deve estar no formato AAAA-MM-DD (recebido: " + valor + ")"));
        }
    }
}
