package com.planningapp.service;

import com.planningapp.dto.EventoSala;
import com.planningapp.dto.LoteEventosSala;
import com.planningapp.dto.ParticipanteDTO;
import com.planningapp.dto.TarefaDTO;
import com.planningapp.dto.TipoEventoSala;
import com.planningapp.model.Participante;
import com.planningapp.model.Perfil;
import com.planningapp.model.StatusTarefa;
import com.planningapp.model.Tarefa;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.EstimativaRepository;
import com.planningapp.persistence.ParticipanteRepository;
import com.planningapp.persistence.TarefaRepository;
import com.planningapp.persistence.UsuarioRepository;
import com.planningapp.security.Papeis;
import com.planningapp.service.exception.NegocioException;
import com.planningapp.service.exception.RecursoNaoEncontradoException;
import com.planningapp.service.mapper.PlanningMapper;
import com.planningapp.service.presenca.PresencaSala;
import com.planningapp.service.votacao.AcompanhamentoVotacao;
import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Quem pode votar numa tarefa. Os JOGADORes são vinculados automaticamente ao entrar na sala
 * ({@link SalaService#entrar}) e quando o moderador cria tarefas; aqui o moderador remove ou readmite.
 */
@Stateless
@RolesAllowed({Papeis.JOGADOR, Papeis.OBSERVADOR, Papeis.ADMIN, Papeis.SUPER, Papeis.TESTE})
public class ParticipanteService {

    @Inject
    private TarefaRepository tarefaRepository;

    @Inject
    private ParticipanteRepository participanteRepository;

    @Inject
    private EstimativaRepository estimativaRepository;

    @Inject
    private UsuarioRepository usuarioRepository;

    @Inject
    private PresencaSala presenca;

    @Inject
    private AcompanhamentoVotacao acompanhamento;

    @Inject
    private Event<EventoSala> eventos;

    @Inject
    private Event<LoteEventosSala> lotes;

    @Resource
    private SessionContext sessionContext;

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<ParticipanteDTO> listar(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefa(salaId, tarefaId);
        Set<String> online = Set.copyOf(presenca.online(salaId));
        return participanteRepository.listarDaTarefa(tarefa.getId()).stream()
                .map(p -> new ParticipanteDTO(p.getUsuario().getUsuario(), p.isAtivo(), online.contains(p.getUsuario().getUsuario())))
                .toList();
    }

    /**
     * Tira o jogador da votação (ex.: saiu da reunião e travaria o "todos votaram"). O voto dele na rodada
     * atual é descartado, como no original; rodadas anteriores ficam no histórico. Se ele era o único que
     * faltava, a fase fica completa e a sala recebe {@code TODOS_VOTARAM} logo depois do aviso de remoção.
     */
    @RolesAllowed(Papeis.ADMIN)
    public List<ParticipanteDTO> remover(Long salaId, Long tarefaId, String nome) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        Usuario usuario = buscarUsuario(nome);
        Participante participante = participanteRepository.buscar(tarefa.getId(), usuario.getId())
                .filter(Participante::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException(nome + " não é participante desta tarefa"));
        boolean jaCompleta = acompanhamento.completa(tarefa, false);

        participante.remover();
        estimativaRepository.buscar(tarefa.getId(), usuario.getId(), tarefa.getRodadaAtual())
                .ifPresent(estimativaRepository::remover);

        TarefaDTO dto = PlanningMapper.toDTO(tarefa);
        List<EventoSala> lote = new ArrayList<>();
        lote.add(EventoSala.participante(TipoEventoSala.PARTICIPANTE_REMOVIDO, dto, nome));
        if (!jaCompleta && acompanhamento.completa(tarefa, false)) {
            lote.add(EventoSala.deTarefa(TipoEventoSala.TODOS_VOTARAM, dto));
        }
        lotes.fire(new LoteEventosSala(lote));
        return listar(salaId, tarefaId);
    }

    /** Readmite quem foi removido, ou inclui um JOGADOR que não estava vinculado. */
    @RolesAllowed(Papeis.ADMIN)
    public List<ParticipanteDTO> adicionar(Long salaId, Long tarefaId, String nome) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        Usuario usuario = buscarUsuario(nome);
        if (usuario.getPerfil() != Perfil.JOGADOR) {
            throw new NegocioException("Só JOGADORes participam da votação (" + nome + " é " + usuario.getPerfil() + ")");
        }
        participanteRepository.buscar(tarefa.getId(), usuario.getId()).ifPresentOrElse(p -> {
            if (p.isAtivo()) {
                throw new NegocioException(nome + " já participa desta tarefa");
            }
            p.readmitir();
        }, () -> participanteRepository.salvar(new Participante(tarefa, usuario)));

        eventos.fire(EventoSala.participante(TipoEventoSala.PARTICIPANTE_ADICIONADO, PlanningMapper.toDTO(tarefa), nome));
        return listar(salaId, tarefaId);
    }

    private Tarefa buscarTarefa(Long salaId, Long tarefaId) {
        return tarefaRepository.buscarPorId(tarefaId)
                .filter(t -> t.getSala().getId().equals(salaId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tarefa " + tarefaId + " não encontrada na sala " + salaId));
    }

    /** Participantes só mudam enquanto a tarefa não foi estimada: depois disso a votação é histórico. */
    private Tarefa buscarTarefaDoModerador(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefa(salaId, tarefaId);
        SalaService.exigirModerador(tarefa.getSala(), sessionContext.getCallerPrincipal().getName());
        if (tarefa.getStatus() == StatusTarefa.ESTIMADA) {
            throw new NegocioException("A tarefa já foi estimada");
        }
        return tarefa;
    }

    private Usuario buscarUsuario(String nome) {
        return usuarioRepository.buscarPorUsuario(nome)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + nome));
    }
}
