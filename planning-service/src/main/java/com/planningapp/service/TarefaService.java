package com.planningapp.service;

import com.planningapp.dto.CriarTarefaRequest;
import com.planningapp.dto.EnvioRedmineDTO;
import com.planningapp.dto.EventoSala;
import com.planningapp.dto.ResultadoImportacaoDTO;
import com.planningapp.dto.TarefaDTO;
import com.planningapp.dto.TipoEventoSala;
import com.planningapp.model.Participante;
import com.planningapp.model.Sala;
import com.planningapp.model.StatusTarefa;
import com.planningapp.model.Tarefa;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.EstimativaRepository;
import com.planningapp.persistence.MembroSalaRepository;
import com.planningapp.persistence.ParticipanteRepository;
import com.planningapp.persistence.SalaRepository;
import com.planningapp.persistence.TarefaRepository;
import com.planningapp.security.Papeis;
import com.planningapp.service.exception.DadosInvalidosException;
import com.planningapp.service.exception.NegocioException;
import com.planningapp.service.exception.RecursoNaoEncontradoException;
import com.planningapp.service.importacao.ImportacaoTarefas;
import com.planningapp.service.mapper.DadosExtras;
import com.planningapp.service.mapper.PlanningMapper;
import com.planningapp.service.redmine.ConsultaRedmine;
import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Qualquer usuário logado lista as tarefas; só o ADMIN moderador da sala as altera. */
@Stateless
@RolesAllowed({Papeis.JOGADOR, Papeis.OBSERVADOR, Papeis.ADMIN, Papeis.SUPER, Papeis.TESTE})
public class TarefaService {

    @Inject
    private SalaRepository salaRepository;

    @Inject
    private TarefaRepository tarefaRepository;

    @Inject
    private EstimativaRepository estimativaRepository;

    @Inject
    private MembroSalaRepository membroSalaRepository;

    @Inject
    private ParticipanteRepository participanteRepository;

    @Inject
    private RedmineService redmineService;

    @Resource
    private SessionContext sessionContext;

    @Inject
    private Event<EventoSala> eventos;

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<TarefaDTO> listarPorSala(Long salaId) {
        buscarSala(salaId);
        return tarefaRepository.listarPorSala(salaId).stream()
                .map(PlanningMapper::toDTO)
                .toList();
    }

    /** Uma tarefa da sala (o quadro de votação do front recarrega a tarefa a cada evento). */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public TarefaDTO buscar(Long salaId, Long tarefaId) {
        return tarefaRepository.buscarPorId(tarefaId)
                .filter(t -> t.getSala().getId().equals(salaId))
                .map(PlanningMapper::toDTO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tarefa " + tarefaId + " não encontrada na sala " + salaId));
    }

    @RolesAllowed(Papeis.ADMIN)
    public TarefaDTO criar(Long salaId, CriarTarefaRequest request) {
        Sala sala = buscarSalaParaIncluir(salaId);
        String numero = request.numero().trim();
        if (tarefaRepository.existeNumeroNaSala(salaId, numero)) {
            throw new NegocioException("Já existe a tarefa " + numero + " nesta sala");
        }
        Tarefa tarefa = incluir(sala, numero, request.titulo(), request.descricao(), request.sprint(),
                request.dadosExtras(), membroSalaRepository.listarJogadores(salaId), false, null);
        return notificar(TipoEventoSala.TAREFA_CRIADA, PlanningMapper.toDTO(tarefa));
    }

    /** Lista JSON no mesmo formato do cadastro unitário, como o {@code /importar} do original. */
    @RolesAllowed(Papeis.ADMIN)
    public ResultadoImportacaoDTO importar(Long salaId, List<CriarTarefaRequest> itens) {
        return importar(salaId, () -> new Leitura(ImportacaoTarefas.deJson(itens), null));
    }

    /** CSV com cabeçalho {@code numero;titulo;descricao} (o mesmo arquivo que o front do original lia com PapaParse). */
    @RolesAllowed(Papeis.ADMIN)
    public ResultadoImportacaoDTO importarCsv(Long salaId, String csv) {
        return importar(salaId, () -> new Leitura(ImportacaoTarefas.deCsv(csv), null));
    }

    /**
     * Os chamados da consulta do Redmine configurada pelo moderador ({@link RedmineService}). Cada tarefa guarda o
     * JSON do chamado como veio do Redmine ({@code tarefas.json_redmine}).
     */
    @RolesAllowed(Papeis.ADMIN)
    public ResultadoImportacaoDTO importarRedmine(Long salaId) {
        return importar(salaId, () -> {
            ConsultaRedmine.Pagina chamados = redmineService.buscarChamados();
            return new Leitura(ImportacaoTarefas.deJson(chamados.chamados()), chamados.json());
        });
    }

    /** {@code jsonRedmine} nulo: não veio do Redmine (CSV ou JSON). */
    private record Leitura(List<ImportacaoTarefas.Linha> linhas, Map<String, String> jsonRedmine) {
    }

    /**
     * Grava a estimativa final no chamado do Redmine (ver {@link RedmineService#enviarEstimativa}). O front chama
     * logo depois de finalizar; separado da finalização para uma falha do Redmine nunca a desfazer. Só para
     * tarefas importadas do Redmine: o número de uma tarefa de CSV pode não ser um chamado.
     */
    @RolesAllowed(Papeis.ADMIN)
    public EnvioRedmineDTO enviarAoRedmine(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        if (!tarefa.isDoRedmine()) {
            throw new NegocioException("A tarefa " + tarefa.getNumero() + " não foi importada do Redmine");
        }
        if (tarefa.getStatus() != StatusTarefa.ESTIMADA) {
            throw new NegocioException("Apenas tarefas estimadas são enviadas ao Redmine");
        }
        return redmineService.enviarEstimativa(tarefa.getNumero(), tarefa.getPontosFinais(), tarefa.getHorasFinais(),
                tarefa.getHorasTesteFinais());
    }

    /**
     * Relê o chamado no Redmine e grava na tarefa o título, a descrição e os campos como estão agora, para o painel
     * de detalhe de uma tarefa já estimada mostrar o chamado atualizado (ex.: a situação depois do envio).
     */
    @RolesAllowed(Papeis.ADMIN)
    public TarefaDTO atualizarDoRedmine(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        if (!tarefa.isDoRedmine()) {
            throw new NegocioException("A tarefa " + tarefa.getNumero() + " não foi importada do Redmine");
        }
        ConsultaRedmine.ChamadoLido lido = redmineService.buscarChamado(tarefa.getNumero());
        String titulo = lido.tarefa().titulo() == null ? "" : lido.tarefa().titulo().trim();
        tarefa.atualizarDoRedmine(titulo.isEmpty() ? tarefa.getTitulo() : titulo.substring(0, Math.min(255, titulo.length())),
                lido.tarefa().descricao(), DadosExtras.paraJson(lido.tarefa().dadosExtras()), lido.json());
        return PlanningMapper.toDTO(tarefa);
    }

    /** Imagem anexada ao chamado da tarefa, para o painel de detalhe (ver {@link RedmineService#baixarAnexo}). */
    @RolesAllowed(Papeis.ADMIN)
    public RedmineService.Arquivo baixarAnexoRedmine(Long salaId, Long tarefaId, String nome) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        if (!tarefa.isDoRedmine()) {
            throw new NegocioException("A tarefa " + tarefa.getNumero() + " não foi importada do Redmine");
        }
        return redmineService.baixarAnexo(tarefa.getNumero(), nome);
    }

    /**
     * Tudo ou nada: se alguma linha for inválida, nada é importado e a resposta traz todos os erros.
     * Números que já existem na sala (ou se repetem no arquivo) são ignorados, como no original.
     */
    private ResultadoImportacaoDTO importar(Long salaId, Supplier<Leitura> leitura) {
        Sala sala = buscarSalaParaIncluir(salaId);
        Leitura lida;
        try {
            lida = leitura.get();
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException("Arquivo inválido", List.of(e.getMessage()));
        }
        List<ImportacaoTarefas.Linha> linhas = lida.linhas();
        boolean doRedmine = lida.jsonRedmine() != null;
        List<String> erros = ImportacaoTarefas.validar(linhas);
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException("Nenhuma tarefa foi importada: corrija os erros e envie de novo", erros);
        }

        Set<String> existentes = new HashSet<>(tarefaRepository.numerosDaSala(salaId));
        List<Usuario> jogadores = membroSalaRepository.listarJogadores(salaId);
        List<TarefaDTO> importadas = new ArrayList<>();
        List<String> ignoradas = new ArrayList<>();
        for (ImportacaoTarefas.Linha linha : linhas) {
            String numero = linha.numero().trim();
            if (!existentes.add(numero)) {
                ignoradas.add(numero);
                continue;
            }
            importadas.add(PlanningMapper.toDTO(incluir(sala, numero, linha.titulo(), linha.descricao(), linha.sprint(),
                    linha.dadosExtras(), jogadores, doRedmine, doRedmine ? lida.jsonRedmine().get(linha.numero()) : null)));
        }

        if (!importadas.isEmpty()) {
            eventos.fire(EventoSala.tarefasImportadas(salaId));
        }
        String mensagem = importadas.size() + " tarefa(s) importada(s)."
                + (ignoradas.isEmpty() ? "" : " " + ignoradas.size() + " ignorada(s) por já existirem.");
        return new ResultadoImportacaoDTO(mensagem, importadas, ignoradas);
    }

    /** Cria na fila e vincula os JOGADORes que já entraram na sala, como participantes. */
    private Tarefa incluir(Sala sala, String numero, String titulo, String descricao, String sprint,
                           Map<String, String> dadosExtras, List<Usuario> jogadores, boolean doRedmine,
                           String jsonRedmine) {
        Tarefa tarefa = new Tarefa(numero, titulo.trim(), semBrancos(descricao), semBrancos(sprint));
        tarefa.definirDadosExtras(DadosExtras.paraJson(dadosExtras));
        if (doRedmine) {
            tarefa.marcarComoDoRedmine(jsonRedmine);
        }
        sala.adicionarTarefa(tarefa);
        tarefaRepository.salvar(tarefa);
        jogadores.forEach(j -> participanteRepository.salvar(new Participante(tarefa, j)));
        return tarefa;
    }

    private static String semBrancos(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Sala buscarSalaParaIncluir(Long salaId) {
        Sala sala = buscarSala(salaId);
        SalaService.exigirModerador(sala, usuarioLogado());
        if (!sala.isAtiva()) {
            throw new NegocioException("Não é possível adicionar tarefas a uma sala inativa");
        }
        return sala;
    }

    /**
     * Só uma tarefa pode estar em votação por vez em cada sala (mesma regra do Planning original).
     * Tarefas puladas também podem ser liberadas de novo, numa rodada nova.
     */
    @RolesAllowed(Papeis.ADMIN)
    public TarefaDTO liberar(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        if (tarefa.getStatus() != StatusTarefa.FILA && tarefa.getStatus() != StatusTarefa.PULADA) {
            throw new NegocioException("Apenas tarefas na fila ou puladas podem ser liberadas");
        }
        boolean existeLiberada = tarefaRepository.listarPorSala(salaId).stream()
                .anyMatch(t -> t.getStatus() == StatusTarefa.LIBERADA);
        if (existeLiberada) {
            throw new NegocioException("Já existe uma tarefa em votação nesta sala");
        }
        tarefa.liberar();
        return notificar(TipoEventoSala.TAREFA_LIBERADA, PlanningMapper.toDTO(tarefa));
    }

    /** O moderador decide os valores finais, normalmente olhando o resumo da votação (média e sugestão). */
    @RolesAllowed(Papeis.ADMIN)
    public TarefaDTO finalizar(Long salaId, Long tarefaId, Integer pontosFinais, Integer horasFinais,
                               Integer horasTesteFinais) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        if (!tarefa.emVotacao()) {
            throw new NegocioException("Apenas tarefas em votação podem ser finalizadas");
        }
        tarefa.finalizar(pontosFinais, horasFinais, horasTesteFinais);
        return notificar(TipoEventoSala.TAREFA_FINALIZADA, PlanningMapper.toDTO(tarefa));
    }

    /**
     * Encerra sem estimativa (ex.: faltou informação para estimar). Vale para tarefas na fila ou em votação;
     * a sala fica livre para liberar a próxima. Diferente do original, os votos não são apagados: ficam como
     * histórico da rodada, e se a tarefa for liberada de novo a votação recomeça numa rodada nova.
     */
    @RolesAllowed(Papeis.ADMIN)
    public TarefaDTO pular(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        if (tarefa.getStatus() == StatusTarefa.ESTIMADA || tarefa.getStatus() == StatusTarefa.PULADA) {
            throw new NegocioException("A tarefa já foi encerrada (" + tarefa.getStatus() + ")");
        }
        tarefa.pular();
        return notificar(TipoEventoSala.TAREFA_PULADA, PlanningMapper.toDTO(tarefa));
    }

    @RolesAllowed(Papeis.ADMIN)
    public void excluir(Long salaId, Long tarefaId) {
        Tarefa tarefa = buscarTarefaDoModerador(salaId, tarefaId);
        TarefaDTO dto = PlanningMapper.toDTO(tarefa);
        estimativaRepository.removerDaTarefa(tarefa.getId());
        participanteRepository.removerDaTarefa(tarefa.getId());
        tarefaRepository.remover(tarefa);
        notificar(TipoEventoSala.TAREFA_EXCLUIDA, dto);
    }

    /**
     * Dispara o evento CDI. Os observers usam {@code during = AFTER_SUCCESS}, então a notificação só sai
     * depois do commit; se a transação falhar, ninguém é avisado de algo que não aconteceu.
     */
    private TarefaDTO notificar(TipoEventoSala tipo, TarefaDTO tarefa) {
        eventos.fire(EventoSala.deTarefa(tipo, tarefa));
        return tarefa;
    }

    private Sala buscarSala(Long salaId) {
        return salaRepository.buscarPorId(salaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala não encontrada: " + salaId));
    }

    /** Busca a tarefa garantindo que ela pertence à sala e que o usuário logado é o moderador dessa sala. */
    private Tarefa buscarTarefaDoModerador(Long salaId, Long tarefaId) {
        Tarefa tarefa = tarefaRepository.buscarPorId(tarefaId)
                .filter(t -> t.getSala().getId().equals(salaId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tarefa " + tarefaId + " não encontrada na sala " + salaId));
        SalaService.exigirModerador(tarefa.getSala(), usuarioLogado());
        return tarefa;
    }

    private String usuarioLogado() {
        return sessionContext.getCallerPrincipal().getName();
    }
}
