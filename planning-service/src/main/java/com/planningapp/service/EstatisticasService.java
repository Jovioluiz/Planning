package com.planningapp.service;

import com.planningapp.dto.EstatisticasDTO;
import com.planningapp.model.Estimativa;
import com.planningapp.model.StatusTarefa;
import com.planningapp.model.Tarefa;
import com.planningapp.persistence.EstimativaRepository;
import com.planningapp.persistence.TarefaRepository;
import com.planningapp.security.Papeis;
import com.planningapp.service.estatisticas.CalculadoraEstatisticas;
import com.planningapp.service.estatisticas.CalculadoraEstatisticas.Filtro;
import com.planningapp.service.estatisticas.CalculadoraEstatisticas.TarefaEncerrada;
import com.planningapp.service.estatisticas.CalculadoraEstatisticas.Voto;
import com.planningapp.service.exception.DadosInvalidosException;
import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Estatísticas das tarefas encerradas. SUPER vê todas as salas; ADMIN vê as salas que modera, como no original.
 * Aqui só carrega os dados (duas consultas); as regras ficam em {@link CalculadoraEstatisticas}.
 */
@Stateless
@RolesAllowed({Papeis.ADMIN, Papeis.SUPER})
public class EstatisticasService {

    /** Os filtros {@code de}/{@code ate} são dias no fuso do time, como no original. */
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Inject
    private TarefaRepository tarefaRepository;

    @Inject
    private EstimativaRepository estimativaRepository;

    @Resource
    private SessionContext sessionContext;

    /** Todos os filtros são opcionais; {@code ate} inclui o dia inteiro. */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public EstatisticasDTO calcular(Long salaId, String sprint, LocalDate de, LocalDate ate) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new DadosInvalidosException("Período inválido", List.of("A data inicial (" + de + ") é depois da final (" + ate + ")"));
        }
        String moderador = sessionContext.isCallerInRole(Papeis.SUPER) ? null : sessionContext.getCallerPrincipal().getName();

        Map<Long, List<Voto>> votosPorTarefa = estimativaRepository.listarDaRodadaFinalDasEstimadas(moderador).stream()
                .collect(Collectors.groupingBy(e -> e.getTarefa().getId(),
                        Collectors.mapping(EstatisticasService::toVoto, Collectors.toList())));

        List<TarefaEncerrada> tarefas = tarefaRepository.listarEncerradas(moderador).stream()
                .map(t -> toTarefa(t, votosPorTarefa.getOrDefault(t.getId(), List.of())))
                .toList();

        Instant inicio = de != null ? de.atStartOfDay(ZONA).toInstant() : null;
        Instant fim = ate != null ? ate.plusDays(1).atStartOfDay(ZONA).toInstant() : null;
        return CalculadoraEstatisticas.calcular(tarefas, new Filtro(salaId, sprint, inicio, fim));
    }

    private static Voto toVoto(Estimativa e) {
        return new Voto(e.getUsuario().getUsuario(), e.getUsuario().getPerfil().name(),
                e.getPontos(), e.getHoras(), e.getHorasTeste());
    }

    private static TarefaEncerrada toTarefa(Tarefa t, List<Voto> votos) {
        return new TarefaEncerrada(t.getId(), t.getNumero(), t.getTitulo(), t.getSprint(),
                t.getSala().getId(), t.getSala().getNome(), t.getStatus() == StatusTarefa.PULADA, t.getRodadaAtual(),
                t.getPontosFinais(), t.getHorasFinais(), t.getHorasTesteFinais(),
                t.getLiberadaEm(), t.getEncerradaEm(), votos);
    }
}
