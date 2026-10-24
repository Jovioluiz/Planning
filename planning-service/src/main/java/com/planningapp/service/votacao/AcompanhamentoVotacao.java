package com.planningapp.service.votacao;

import com.planningapp.model.Estimativa;
import com.planningapp.model.FaseHorasTeste;
import com.planningapp.model.Tarefa;
import com.planningapp.persistence.EstimativaRepository;
import com.planningapp.persistence.ParticipanteRepository;
import com.planningapp.security.Papeis;
import com.planningapp.service.presenca.PresencaSala;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Quem ainda falta votar. Bean CDI comum (não EJB): roda na transação e com a identidade de quem chama,
 * e é compartilhado pelos EJBs que mexem nos votos ou nos participantes.
 *
 * <ul>
 *   <li>Votação principal: espera os participantes ativos da tarefa que estão online na sala.</li>
 *   <li>Horas de teste: espera os TESTEs online (testadores não são participantes, como no original).</li>
 * </ul>
 */
@ApplicationScoped
public class AcompanhamentoVotacao {

    @Inject
    private PresencaSala presenca;

    @Inject
    private ParticipanteRepository participanteRepository;

    @Inject
    private EstimativaRepository estimativaRepository;

    /** Nulo se nenhuma fase da votação principal está aberta para voto. */
    public Progresso principal(Tarefa tarefa, List<Estimativa> estimativas) {
        Predicate<Estimativa> votou = votouNaFaseAberta(tarefa);
        if (votou == null) {
            return null;
        }
        Set<String> esperados = new HashSet<>(presenca.onlineComPapel(tarefa.getSala().getId(), Papeis.JOGADOR));
        esperados.retainAll(participanteRepository.ativos(tarefa.getId()));
        return Progresso.calcular(esperados, votantes(estimativas, votou));
    }

    /** Nulo se a trilha de horas de teste não está em votação. */
    public Progresso teste(Tarefa tarefa, List<Estimativa> estimativas) {
        Predicate<Estimativa> votou = votouNasHorasTeste(tarefa);
        return votou == null ? null : Progresso.calcular(
                presenca.onlineComPapel(tarefa.getSala().getId(), Papeis.TESTE), votantes(estimativas, votou));
    }

    /**
     * Usado para avisar só na transição para "completa". Chame antes de alterar votos ou participantes:
     * a consulta faz flush das mudanças pendentes.
     */
    public boolean completa(Tarefa tarefa, boolean teste) {
        List<Estimativa> estimativas = estimativaRepository.listarDaRodada(tarefa.getId(), tarefa.getRodadaAtual());
        Progresso progresso = teste ? teste(tarefa, estimativas) : principal(tarefa, estimativas);
        return progresso != null && progresso.todosVotaram();
    }

    /** O usuário já votou na fase aberta (da trilha indicada)? */
    public boolean jaVotou(Tarefa tarefa, String usuario, boolean teste) {
        Predicate<Estimativa> votou = teste ? votouNasHorasTeste(tarefa) : votouNaFaseAberta(tarefa);
        return votou != null && votantes(
                estimativaRepository.listarDaRodada(tarefa.getId(), tarefa.getRodadaAtual()), votou).contains(usuario);
    }

    private static Predicate<Estimativa> votouNaFaseAberta(Tarefa tarefa) {
        if (!tarefa.emVotacao() || tarefa.getFase() == null) {
            return null;
        }
        return switch (tarefa.getFase()) {
            case PONTOS -> Estimativa::votouPontos;
            case HORAS -> Estimativa::votouHoras;
            default -> null;
        };
    }

    private static Predicate<Estimativa> votouNasHorasTeste(Tarefa tarefa) {
        return tarefa.emVotacao() && tarefa.getFaseHorasTeste() == FaseHorasTeste.VOTACAO ? Estimativa::votouHorasTeste : null;
    }

    private static Set<String> votantes(List<Estimativa> estimativas, Predicate<Estimativa> votou) {
        return estimativas.stream().filter(votou).map(e -> e.getUsuario().getUsuario()).collect(Collectors.toSet());
    }
}
