package com.planningapp.service;

import com.planningapp.dto.CriarSalaRequest;
import com.planningapp.dto.EventoSala;
import com.planningapp.dto.SalaDTO;
import com.planningapp.dto.SalaModeradaDTO;
import com.planningapp.model.MembroSala;
import com.planningapp.model.Participante;
import com.planningapp.model.Perfil;
import com.planningapp.model.Sala;
import com.planningapp.model.StatusTarefa;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.MembroSalaRepository;
import com.planningapp.persistence.ParticipanteRepository;
import com.planningapp.persistence.SalaRepository;
import com.planningapp.persistence.TarefaRepository;
import com.planningapp.persistence.UsuarioRepository;
import com.planningapp.security.Papeis;
import com.planningapp.service.exception.AcessoNegadoException;
import com.planningapp.service.exception.NegocioException;
import com.planningapp.service.exception.RecursoNaoEncontradoException;
import com.planningapp.service.mapper.PlanningMapper;
import com.planningapp.service.presenca.PresencaSala;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Logger;

/**
 * EJB sem estado: cada método público roda numa transação JTA aberta pelo container
 * (padrão {@code REQUIRED}), sem precisar de {@code @Transactional}.
 *
 * <p>O {@code @RolesAllowed} da classe vale para todos os métodos (qualquer usuário logado);
 * os métodos que exigem ADMIN ou acesso público sobrescrevem.</p>
 */
@Stateless
@RolesAllowed({Papeis.JOGADOR, Papeis.OBSERVADOR, Papeis.ADMIN, Papeis.SUPER, Papeis.TESTE})
public class SalaService {

    private static final Logger LOG = Logger.getLogger(SalaService.class.getName());

    @Inject
    private SalaRepository salaRepository;

    @Inject
    private UsuarioRepository usuarioRepository;

    @Inject
    private TarefaRepository tarefaRepository;

    @Inject
    private MembroSalaRepository membroSalaRepository;

    @Inject
    private ParticipanteRepository participanteRepository;

    @Inject
    private PresencaSala presenca;

    @Inject
    private InativacaoAutomatica inativacao;

    @Resource
    private SessionContext sessionContext;

    @Inject
    private Event<EventoSala> eventos;

    @RolesAllowed(Papeis.ADMIN)
    public SalaDTO criar(CriarSalaRequest request) {
        Usuario moderador = usuarioRepository.buscarPorUsuario(usuarioLogado())
                .orElseThrow(() -> new AcessoNegadoException("Usuário do token não existe mais"));
        Sala sala = salaRepository.salvar(new Sala(request.nome().trim(), moderador));
        return PlanningMapper.toDTO(sala, 0);
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<SalaDTO> listarAtivas() {
        return salaRepository.listarAtivas().stream()
                .map(s -> PlanningMapper.toDTO(s, salaRepository.contarTarefas(s.getId())))
                .toList();
    }

    /** Público, como no Planning original: a página /sala/{codigo} mostra o nome antes do login. */
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public SalaDTO buscarPorCodigo(String codigo) {
        Sala sala = salaRepository.buscarPorCodigo(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala não encontrada: " + codigo));
        return PlanningMapper.toDTO(sala, salaRepository.contarTarefas(sala.getId()));
    }

    /**
     * Entra na sala pelo código (o link {@code /sala/{codigo}} do front). Idempotente. Um JOGADOR que entra
     * pela primeira vez vira participante de todas as tarefas da sala ainda não estimadas, como no original;
     * as tarefas criadas depois também o incluem ({@link TarefaService#criar}).
     */
    public SalaDTO entrar(String codigo) {
        Sala sala = salaRepository.buscarPorCodigo(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala não encontrada: " + codigo));
        if (!sala.isAtiva()) {
            throw new NegocioException("A sala está inativa");
        }
        Usuario usuario = usuarioRepository.buscarPorUsuario(usuarioLogado())
                .orElseThrow(() -> new AcessoNegadoException("Usuário do token não existe mais"));

        if (!membroSalaRepository.existe(sala.getId(), usuario.getId())) {
            membroSalaRepository.salvar(new MembroSala(sala, usuario));
        }
        // A cada entrada (não só na primeira): completa os vínculos que faltarem, o que também corrige dados
        // migrados do original em que o membro não tinha participação numa tarefa pendente. Quem foi removido
        // tem o registro (com removidoEm) e por isso não é readmitido aqui.
        if (usuario.getPerfil() == Perfil.JOGADOR) {
            tarefaRepository.listarPorSala(sala.getId()).stream()
                    .filter(t -> t.getStatus() != StatusTarefa.ESTIMADA)
                    .filter(t -> participanteRepository.buscar(t.getId(), usuario.getId()).isEmpty())
                    .forEach(t -> participanteRepository.salvar(new Participante(t, usuario)));
        }
        return PlanningMapper.toDTO(sala, salaRepository.contarTarefas(sala.getId()));
    }

    /**
     * Usado pelo WebSocket na abertura da conexão. Lá a chamada não tem identidade do Jakarta Security
     * (o token é validado pelo próprio endpoint), por isso é {@code @PermitAll}.
     */
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public boolean existeAtiva(Long salaId) {
        return salaRepository.buscarPorId(salaId).map(Sala::isAtiva).orElse(false);
    }

    /**
     * Abertura do WebSocket da sala. Sala ativa aceita qualquer um (o token já foi validado). Sala inativa só aceita
     * o moderador, e volta a ficar ativa se ainda tem tarefas na fila ou em votação: foi inativada porque ele saiu
     * (um refresh demorado, a rede que caiu, um reinício do servidor) e ele voltou. Como o original, que reativava as
     * salas do moderador quando ele reconectava ({@code reativarSalasComTarefasAbertas}). Sem isso o moderador que
     * está na tela da sala ficava de fora, e os jogadores recebiam "A sala está inativa" ao entrar pelo link.
     */
    @PermitAll
    public boolean aceitaConexao(Long salaId, String usuario) {
        return salaRepository.buscarPorId(salaId).map(sala -> {
            if (sala.isAtiva()) {
                return true;
            }
            if (!sala.isModeradaPor(usuario) || salaRepository.contarPendentes(salaId) == 0) {
                return false;
            }
            sala.setAtiva(true);
            LOG.info(() -> "Sala " + salaId + " (" + sala.getNome() + ") reativada: o moderador " + usuario + " voltou");
            return true;
        }).orElse(false);
    }

    @RolesAllowed(Papeis.ADMIN)
    public void desativar(Long salaId) {
        Sala sala = salaRepository.buscarPorId(salaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala não encontrada: " + salaId));
        exigirModerador(sala, usuarioLogado());
        sala.setAtiva(false);
        eventos.fire(EventoSala.salaDesativada(salaId));
    }

    /** Reabre uma sala desativada (o original reativava sozinho; aqui é uma ação explícita do moderador). */
    @RolesAllowed(Papeis.ADMIN)
    public SalaModeradaDTO reativar(Long salaId) {
        Sala sala = salaRepository.buscarPorId(salaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala não encontrada: " + salaId));
        exigirModerador(sala, usuarioLogado());
        sala.setAtiva(true);
        return toModeradaDTO(sala);
    }

    /**
     * "Minhas salas" do moderador, ativas e inativas, com quem já entrou (a tela de salas do front). Como no
     * original ({@code buscarSalasComReativacao}), reativa as salas inativas que ainda têm tarefas na fila ou em
     * votação: foram inativadas porque o moderador saiu, e ele voltou.
     */
    @RolesAllowed(Papeis.ADMIN)
    public List<SalaModeradaDTO> minhas() {
        List<Sala> salas = salaRepository.listarDoModerador(usuarioLogado());
        salas.stream()
                .filter(s -> !s.isAtiva() && salaRepository.contarPendentes(s.getId()) > 0)
                .forEach(s -> {
                    s.setAtiva(true);
                    LOG.info(() -> "Sala " + s.getId() + " (" + s.getNome() + ") reativada: o moderador "
                            + s.getModerador().getUsuario() + " abriu \"Minhas salas\" e ela tem tarefas pendentes");
                });
        return salas.stream().map(this::toModeradaDTO).toList();
    }

    /**
     * Chamado pela camada web quando a última conexão de alguém na sala fecha. Se era o moderador, agenda a
     * inativação. {@code @PermitAll} porque roda no fechamento do WebSocket, sem usuário autenticado.
     *
     * <p>Com transação (REQUIRED, o padrão), não SUPPORTS: chamado do WebSocket não há transação em curso, e sem
     * ela o persistence context fecha logo após a busca, e o {@code moderador} (LAZY) estoura
     * LazyInitializationException. O CompatibilidadeFrontIT pegou isso.</p>
     */
    @PermitAll
    public void aoSairDaSala(Long salaId, String usuario) {
        salaRepository.buscarPorId(salaId)
                .filter(s -> s.isAtiva() && s.isModeradaPor(usuario))
                .ifPresent(s -> inativacao.agendar(salaId, usuario));
    }

    /** Executado pelo agendador: inativa só se o moderador continua fora (em qualquer nó do cluster). */
    @PermitAll
    public void inativarSeModeradorAusente(Long salaId, String moderador) {
        salaRepository.buscarPorId(salaId)
                .filter(s -> s.isAtiva() && s.isModeradaPor(moderador))
                .filter(s -> !presenca.online(salaId).contains(moderador))
                .ifPresent(s -> {
                    s.setAtiva(false);
                    eventos.fire(EventoSala.salaDesativada(salaId));
                    LOG.info(() -> "Sala " + salaId + " (" + s.getNome() + ") inativada: o moderador " + moderador
                            + " saiu e não voltou");
                });
    }

    /** Quem está conectado no WebSocket da sala agora, em qualquer nó do cluster. */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<String> online(Long salaId) {
        salaRepository.buscarPorId(salaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala não encontrada: " + salaId));
        return presenca.online(salaId);
    }

    private SalaModeradaDTO toModeradaDTO(Sala sala) {
        return new SalaModeradaDTO(sala.getId(), sala.getNome(), sala.getCodigo(), sala.isAtiva(), sala.getCriadaEm(),
                (int) salaRepository.contarTarefas(sala.getId()), (int) salaRepository.contarPendentes(sala.getId()),
                membroSalaRepository.listarNomes(sala.getId()));
    }

    static void exigirModerador(Sala sala, String usuario) {
        if (!sala.isModeradaPor(usuario)) {
            throw new AcessoNegadoException("Apenas o moderador da sala pode fazer isso");
        }
    }

    private String usuarioLogado() {
        return sessionContext.getCallerPrincipal().getName();
    }
}
