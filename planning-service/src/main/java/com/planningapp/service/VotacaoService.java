package com.planningapp.service;

import com.planningapp.dto.EventoSala;
import com.planningapp.dto.LoteEventosSala;
import com.planningapp.dto.TarefaDTO;
import com.planningapp.dto.TipoEventoSala;
import com.planningapp.dto.VotacaoDTO;
import com.planningapp.dto.VotoDTO;
import com.planningapp.dto.VotoTesteDTO;
import com.planningapp.model.Estimativa;
import com.planningapp.model.FaseHorasTeste;
import com.planningapp.model.FaseVotacao;
import com.planningapp.model.Perfil;
import com.planningapp.model.StatusTarefa;
import com.planningapp.model.Tarefa;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.EstimativaRepository;
import com.planningapp.persistence.ParticipanteRepository;
import com.planningapp.persistence.TarefaRepository;
import com.planningapp.persistence.UsuarioRepository;
import com.planningapp.security.Papeis;
import com.planningapp.service.exception.AcessoNegadoException;
import com.planningapp.service.exception.ConflitoException;
import com.planningapp.service.exception.NegocioException;
import com.planningapp.service.exception.RecursoNaoEncontradoException;
import com.planningapp.service.mapper.PlanningMapper;
import com.planningapp.service.votacao.AcompanhamentoVotacao;
import com.planningapp.service.votacao.Cartas;
import com.planningapp.service.votacao.Progresso;
import com.planningapp.service.votacao.ResumoVotacao;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Votação de pontos e horas de uma tarefa liberada.
 *
 * <pre>
 *   PONTOS ──revelarPontos──► PONTOS_REVELADOS ──liberarHoras──► HORAS ──revelarHoras──► HORAS_REVELADAS
 *     ▲                                                                                        │
 *     └──────────────────────────── novaRodada (de qualquer fase) ◄────────────────────────────┘
 * </pre>
 *
 * <p>Só JOGADOR vota, e sempre em nome do usuário do token. Só o moderador da sala conduz as fases.</p>
 *
 * <p>Em paralelo, e independente das fases acima, há a trilha de horas de teste (perfil TESTE):</p>
 * <pre>
 *   (não liberada) ──liberarHorasTeste──► VOTACAO ──revelarHorasTeste──► REVELADA      novaRodada volta para "não liberada"
 * </pre>
 */
@Stateless
@RolesAllowed({Papeis.JOGADOR, Papeis.OBSERVADOR, Papeis.ADMIN, Papeis.SUPER, Papeis.TESTE})
public class VotacaoService {

    @Inject
    private TarefaRepository tarefaRepository;

    @Inject
    private EstimativaRepository estimativaRepository;

    @Inject
    private UsuarioRepository usuarioRepository;

    @Inject
    private ParticipanteRepository participanteRepository;

    @Inject
    private AcompanhamentoVotacao acompanhamento;

    @Inject
    private Event<EventoSala> eventos;

    @Inject
    private Event<LoteEventosSala> lotes;

    @Resource
    private SessionContext sessionContext;

    /**
     * Votos da rodada atual. Até a fase ser revelada, cada um vê só o próprio voto; dos demais aparece
     * apenas se já votaram. OBSERVADOR e TESTE veem tudo, como no Planning original.
     */
    public VotacaoDTO consultar(Long salaId, Long tarefaId) {
        return montarVotacao(buscarTarefa(salaId, tarefaId));
    }

    @RolesAllowed(Papeis.JOGADOR)
    public VotacaoDTO votarPontos(Long salaId, Long tarefaId, int pontos) {
        Tarefa tarefa = buscarTarefa(salaId, tarefaId);
        exigirFase(tarefa, FaseVotacao.PONTOS, "A votação de pontos desta rodada já foi encerrada");
        if (!Cartas.pontosValidos(pontos)) {
            throw new NegocioException("Carta inválida: " + pontos + ". Use " + Cartas.PONTOS + " ou 0 (café)");
        }

        Usuario usuario = usuarioLogado();
        exigirParticipante(tarefa, usuario);
        if (estimativaRepository.buscar(tarefa.getId(), usuario.getId(), tarefa.getRodadaAtual()).isPresent()) {
            throw new ConflitoException("Você já votou nos pontos desta rodada");
        }
        boolean jaCompleta = acompanhamento.completa(tarefa, false);

        Estimativa estimativa = new Estimativa(tarefa, usuario, tarefa.getRodadaAtual());
        estimativa.votarPontos(pontos);
        estimativaRepository.salvar(estimativa);

        return registrarVoto(tarefa, usuario.getUsuario(), false, jaCompleta);
    }

    @RolesAllowed(Papeis.JOGADOR)
    public VotacaoDTO votarHoras(Long salaId, Long tarefaId, int horas) {
        Tarefa tarefa = buscarTarefa(salaId, tarefaId);
        exigirFase(tarefa, FaseVotacao.HORAS, tarefa.getFase() == FaseVotacao.HORAS_REVELADAS
                ? "A votação de horas desta rodada já foi encerrada"
                : "As horas ainda não foram liberadas pelo moderador");
        if (!Cartas.horasValidas(horas)) {
            throw new NegocioException("As horas devem estar entre " + Cartas.HORAS_MINIMO + " e " + Cartas.HORAS_MAXIMO);
        }

        Usuario usuario = usuarioLogado();
        exigirParticipante(tarefa, usuario);
        // Quem entrou na sala já na fase de horas não votou nos pontos: registra café para não travar a rodada
        Estimativa estimativa = estimativaRepository.buscar(tarefa.getId(), usuario.getId(), tarefa.getRodadaAtual())
                .orElseGet(() -> {
                    Estimativa nova = new Estimativa(tarefa, usuario, tarefa.getRodadaAtual());
                    nova.votarPontos(Estimativa.CAFE);
                    return nova;
                });
        if (estimativa.votouHoras()) {
            throw new ConflitoException("Você já votou nas horas desta rodada");
        }
        boolean jaCompleta = acompanhamento.completa(tarefa, false);

        estimativa.votarHoras(horas);
        estimativaRepository.salvar(estimativa);

        return registrarVoto(tarefa, usuario.getUsuario(), false, jaCompleta);
    }

    /** Pode acontecer em qualquer fase da votação principal, desde que o moderador tenha liberado a trilha. */
    @RolesAllowed(Papeis.TESTE)
    public VotacaoDTO votarHorasTeste(Long salaId, Long tarefaId, int horas) {
        Tarefa tarefa = buscarTarefa(salaId, tarefaId);
        exigirFaseTeste(tarefa, FaseHorasTeste.VOTACAO, tarefa.getFaseHorasTeste() == FaseHorasTeste.REVELADA
                ? "A votação de horas de teste desta rodada já foi encerrada"
                : "As horas de teste ainda não foram liberadas pelo moderador");
        if (!Cartas.horasValidas(horas)) {
            throw new NegocioException("As horas devem estar entre " + Cartas.HORAS_MINIMO + " e " + Cartas.HORAS_MAXIMO);
        }

        Usuario usuario = usuarioLogado();
        Estimativa estimativa = estimativaRepository.buscar(tarefa.getId(), usuario.getId(), tarefa.getRodadaAtual())
                .orElseGet(() -> new Estimativa(tarefa, usuario, tarefa.getRodadaAtual()));
        if (estimativa.votouHorasTeste()) {
            throw new ConflitoException("Você já votou nas horas de teste desta rodada");
        }
        boolean jaCompleta = acompanhamento.completa(tarefa, true);

        estimativa.votarHorasTeste(horas);
        estimativaRepository.salvar(estimativa);

        return registrarVoto(tarefa, usuario.getUsuario(), true, jaCompleta);
    }

    /**
     * Chamado pela camada web quando a última conexão de um usuário na sala fecha. Se ele era o único que
     * faltava votar, a fase acabou de ficar completa e a sala é avisada. Quem já tinha votado não muda nada.
     *
     * <p>{@code @PermitAll} porque roda no fechamento do WebSocket, sem usuário autenticado. Não é exposto
     * por REST e só dispara eventos.</p>
     */
    @PermitAll
    public void aoSairDaSala(Long salaId, String usuario) {
        Perfil perfil = usuarioRepository.buscarPorUsuario(usuario).map(Usuario::getPerfil).orElse(null);
        if (perfil != Perfil.JOGADOR && perfil != Perfil.TESTE) {
            return;
        }
        tarefaRepository.listarPorSala(salaId).stream().filter(Tarefa::emVotacao).findFirst().ifPresent(tarefa -> {
            boolean teste = perfil == Perfil.TESTE;
            // Só muda algo se ele era esperado: participante ativo (ou testador) que ainda não tinha votado
            boolean esperado = teste || participanteRepository.ativos(tarefa.getId()).contains(usuario);
            if (!esperado || acompanhamento.jaVotou(tarefa, usuario, teste)) {
                return;
            }
            if (acompanhamento.completa(tarefa, teste)) {
                eventos.fire(EventoSala.deTarefa(teste ? TipoEventoSala.TODOS_TESTADORES_VOTARAM : TipoEventoSala.TODOS_VOTARAM,
                        PlanningMapper.toDTO(tarefa)));
            }
        });
    }

    @RolesAllowed(Papeis.ADMIN)
    public VotacaoDTO liberarHorasTeste(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        exigirEmVotacao(tarefa);
        if (tarefa.getFaseHorasTeste() != null) {
            throw new NegocioException("As horas de teste desta rodada já foram liberadas");
        }
        return avancarTeste(tarefa, FaseHorasTeste.VOTACAO, TipoEventoSala.HORAS_TESTE_LIBERADAS);
    }

    @RolesAllowed(Papeis.ADMIN)
    public VotacaoDTO revelarHorasTeste(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        exigirFaseTeste(tarefa, FaseHorasTeste.VOTACAO, "As horas de teste não estão em votação");
        boolean alguemVotou = estimativaRepository.listarDaRodada(tarefa.getId(), tarefa.getRodadaAtual()).stream()
                .anyMatch(Estimativa::votouHorasTeste);
        if (!alguemVotou) {
            throw new NegocioException("Nenhum testador votou ainda");
        }
        return avancarTeste(tarefa, FaseHorasTeste.REVELADA, TipoEventoSala.HORAS_TESTE_REVELADAS);
    }

    @RolesAllowed(Papeis.ADMIN)
    public VotacaoDTO revelarPontos(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        exigirFase(tarefa, FaseVotacao.PONTOS, "Os pontos desta rodada já foram revelados");
        boolean alguemVotou = estimativaRepository.listarDaRodada(tarefa.getId(), tarefa.getRodadaAtual()).stream()
                .anyMatch(Estimativa::votouPontos);
        if (!alguemVotou) {
            throw new NegocioException("Ninguém votou nos pontos ainda");
        }
        return avancar(tarefa, FaseVotacao.PONTOS_REVELADOS, TipoEventoSala.PONTOS_REVELADOS);
    }

    @RolesAllowed(Papeis.ADMIN)
    public VotacaoDTO liberarHoras(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        exigirFase(tarefa, FaseVotacao.PONTOS_REVELADOS, "Revele os pontos antes de liberar as horas");
        return avancar(tarefa, FaseVotacao.HORAS, TipoEventoSala.HORAS_LIBERADAS);
    }

    @RolesAllowed(Papeis.ADMIN)
    public VotacaoDTO revelarHoras(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        exigirFase(tarefa, FaseVotacao.HORAS, "As horas não estão em votação");
        return avancar(tarefa, FaseVotacao.HORAS_REVELADAS, TipoEventoSala.HORAS_REVELADAS);
    }

    /** Recomeça pelos pontos. Os votos da rodada anterior continuam no banco como histórico. */
    @RolesAllowed(Papeis.ADMIN)
    public VotacaoDTO novaRodada(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        exigirEmVotacao(tarefa);
        tarefa.novaRodada();
        eventos.fire(EventoSala.deTarefa(TipoEventoSala.NOVA_RODADA, PlanningMapper.toDTO(tarefa)));
        return montarVotacao(tarefa);
    }

    private VotacaoDTO avancar(Tarefa tarefa, FaseVotacao fase, TipoEventoSala evento) {
        tarefa.avancarPara(fase);
        eventos.fire(EventoSala.deTarefa(evento, PlanningMapper.toDTO(tarefa)));
        return montarVotacao(tarefa);
    }

    private VotacaoDTO avancarTeste(Tarefa tarefa, FaseHorasTeste fase, TipoEventoSala evento) {
        tarefa.avancarHorasTestePara(fase);
        eventos.fire(EventoSala.deTarefa(evento, PlanningMapper.toDTO(tarefa)));
        return montarVotacao(tarefa);
    }

    /**
     * Avisa o voto e, se foi ele que completou a fase, avisa "todos votaram" logo em seguida, no mesmo lote
     * para chegar nessa ordem. O aviso sai só na transição: quem vota depois (ex.: alguém que não está
     * conectado na sala) não repete o aviso. A outra saída possível é {@link #aoSairDaSala}.
     *
     * @param teste      voto na trilha de horas de teste
     * @param jaCompleta a fase já estava completa antes deste voto
     */
    private VotacaoDTO registrarVoto(Tarefa tarefa, String usuario, boolean teste, boolean jaCompleta) {
        VotacaoDTO votacao = montarVotacao(tarefa);
        TarefaDTO dto = PlanningMapper.toDTO(tarefa);

        List<EventoSala> lote = new ArrayList<>();
        lote.add(EventoSala.voto(teste ? TipoEventoSala.VOTO_TESTE_REGISTRADO : TipoEventoSala.VOTO_REGISTRADO, dto, usuario));
        boolean completa = Boolean.TRUE.equals(teste ? votacao.todosTestadoresVotaram() : votacao.todosVotaram());
        if (completa && !jaCompleta) {
            lote.add(EventoSala.deTarefa(teste ? TipoEventoSala.TODOS_TESTADORES_VOTARAM : TipoEventoSala.TODOS_VOTARAM, dto));
        }
        lotes.fire(new LoteEventosSala(lote));
        return votacao;
    }

    private VotacaoDTO montarVotacao(Tarefa tarefa) {
        List<Estimativa> estimativas = estimativaRepository.listarDaRodada(tarefa.getId(), tarefa.getRodadaAtual());

        boolean finalizada = tarefa.getStatus() == StatusTarefa.ESTIMADA;
        boolean pontosRevelados = finalizada || (tarefa.getFase() != null && tarefa.getFase().pontosRevelados());
        boolean horasReveladas = finalizada || (tarefa.getFase() != null && tarefa.getFase().horasReveladas());
        boolean horasTesteReveladas = finalizada || tarefa.getFaseHorasTeste() == FaseHorasTeste.REVELADA;
        boolean veTudo = sessionContext.isCallerInRole(Papeis.OBSERVADOR) || sessionContext.isCallerInRole(Papeis.TESTE);
        String eu = sessionContext.getCallerPrincipal().getName();

        // A mesma linha de Estimativa guarda o voto do jogador (pontos/horas) ou do testador (horas de teste)
        List<Estimativa> dosJogadores = estimativas.stream().filter(e -> e.votouPontos() || e.votouHoras()).toList();
        List<Estimativa> dosTestadores = estimativas.stream().filter(Estimativa::votouHorasTeste).toList();

        // Horas de teste: ninguém vê os valores dos outros antes de revelar (nem o moderador), só o próprio
        List<VotoTesteDTO> votosTeste = dosTestadores.stream()
                .map(e -> new VotoTesteDTO(
                        e.getUsuario().getUsuario(),
                        true,
                        horasTesteReveladas || e.getUsuario().getUsuario().equals(eu) ? e.getHorasTeste() : null))
                .toList();

        List<VotoDTO> votos = dosJogadores.stream()
                .map(e -> {
                    boolean meu = e.getUsuario().getUsuario().equals(eu);
                    return voto(tarefa, e, pontosRevelados || veTudo || meu, horasReveladas || veTudo || meu);
                })
                .toList();

        // A rodada anterior já acabou: quem acompanha a votação a vê inteira, como referência (o front mostra
        // "Rodada N-1"). Jogador não, para não ancorar o voto da rodada nova (no original ele também não via).
        boolean moderador = tarefa.getSala().isModeradaPor(eu) || sessionContext.isCallerInRole(Papeis.SUPER);
        List<VotoDTO> rodadaAnterior = tarefa.getRodadaAtual() > 1 && (veTudo || moderador)
                ? estimativaRepository.listarDaRodada(tarefa.getId(), tarefa.getRodadaAtual() - 1).stream()
                        .filter(e -> e.votouPontos() || e.votouHoras())
                        .map(e -> voto(tarefa, e, true, true))
                        .toList()
                : List.of();

        Progresso principal = acompanhamento.principal(tarefa, estimativas);
        Progresso teste = acompanhamento.teste(tarefa, estimativas);

        return new VotacaoDTO(
                tarefa.getId(),
                tarefa.getStatus().name(),
                tarefa.getFase() != null ? tarefa.getFase().name() : null,
                tarefa.getRodadaAtual(),
                votos,
                pontosRevelados ? ResumoVotacao.resumirPontos(dosJogadores.stream().map(Estimativa::getPontos).toList()) : null,
                horasReveladas ? ResumoVotacao.resumirHoras(dosJogadores.stream().map(Estimativa::getHoras).toList()) : null,
                principal != null ? principal.aguardando() : null,
                principal != null ? principal.todosVotaram() : null,
                tarefa.getFaseHorasTeste() != null ? tarefa.getFaseHorasTeste().name() : null,
                votosTeste,
                horasTesteReveladas && !dosTestadores.isEmpty()
                        ? ResumoVotacao.resumirHoras(dosTestadores.stream().map(Estimativa::getHorasTeste).toList())
                        : null,
                teste != null ? teste.aguardando() : null,
                teste != null ? teste.todosVotaram() : null,
                rodadaAnterior);
    }

    /**
     * O tempo de cada voto é contado desde a liberação da tarefa (pontos) e das horas, como no original. É
     * informação de quem votou, não do valor, então não é oculto.
     */
    private static VotoDTO voto(Tarefa tarefa, Estimativa e, boolean vePontos, boolean veHoras) {
        return new VotoDTO(
                e.getUsuario().getUsuario(),
                e.votouPontos(),
                vePontos ? e.getPontos() : null,
                e.votouHoras(),
                veHoras ? e.getHoras() : null,
                e.getRodada(),
                segundosEntre(tarefa.getLiberadaEm(), e.getVotadoEmPontos()),
                segundosEntre(tarefa.getHorasLiberadasEm(), e.getVotadoEmHoras()));
    }

    private static Long segundosEntre(Instant inicio, Instant fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            return null;
        }
        return Duration.between(inicio, fim).toSeconds();
    }

    private static void exigirFaseTeste(Tarefa tarefa, FaseHorasTeste esperada, String mensagem) {
        exigirEmVotacao(tarefa);
        if (tarefa.getFaseHorasTeste() != esperada) {
            throw new NegocioException(mensagem);
        }
    }

    private static void exigirEmVotacao(Tarefa tarefa) {
        if (!tarefa.emVotacao()) {
            throw new NegocioException("A tarefa não está em votação");
        }
    }

    private static void exigirFase(Tarefa tarefa, FaseVotacao esperada, String mensagem) {
        exigirEmVotacao(tarefa);
        if (tarefa.getFase() != esperada) {
            throw new NegocioException(mensagem);
        }
    }

    private Tarefa buscarTarefa(Long salaId, Long tarefaId) {
        return tarefaRepository.buscarPorId(tarefaId)
                .filter(t -> t.getSala().getId().equals(salaId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tarefa " + tarefaId + " não encontrada na sala " + salaId));
    }

    private Tarefa buscarTarefaDoModerador(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefa(salaId, tarefaId);
        SalaService.exigirModerador(tarefa.getSala(), sessionContext.getCallerPrincipal().getName());
        return tarefa;
    }

    /** Como no original: só vota quem é participante (entrou na sala) e não foi removido pelo moderador. */
    private void exigirParticipante(Tarefa tarefa, Usuario usuario) {
        participanteRepository.buscar(tarefa.getId(), usuario.getId())
                .ifPresentOrElse(p -> {
                    if (!p.isAtivo()) {
                        throw new AcessoNegadoException("Você foi removido desta votação");
                    }
                }, () -> {
                    throw new AcessoNegadoException("Você não participa desta tarefa. Entre na sala pelo código primeiro");
                });
    }

    private Usuario usuarioLogado() {
        String nome = sessionContext.getCallerPrincipal().getName();
        return usuarioRepository.buscarPorUsuario(nome)
                .orElseThrow(() -> new AcessoNegadoException("Usuário do token não existe mais"));
    }
}
