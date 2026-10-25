package com.planningapp.service;

import com.planningapp.dto.ConfiguracaoRedmineDTO;
import com.planningapp.dto.CriarTarefaRequest;
import com.planningapp.dto.SalvarConfiguracaoRedmineRequest;
import com.planningapp.dto.TesteRedmineDTO;
import com.planningapp.model.ConfiguracaoRedmine;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.ConfiguracaoRedmineRepository;
import com.planningapp.persistence.UsuarioRepository;
import com.planningapp.security.Papeis;
import com.planningapp.service.exception.DadosInvalidosException;
import com.planningapp.service.exception.NegocioException;
import com.planningapp.service.exception.RecursoNaoEncontradoException;
import com.planningapp.service.importacao.ImportacaoTarefas;
import com.planningapp.service.redmine.ConsultaRedmine;
import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Integração com o Redmine: a configuração de acesso de cada moderador (link da consulta, projeto e chave da API)
 * e a busca dos chamados dessa consulta, para a importação de tarefas ({@link TarefaService#importarRedmine}).
 *
 * <p>A chave fica no banco e nunca volta para o cliente. O Redmine aplica as permissões de quem gerou a chave.</p>
 */
@Stateless
@RolesAllowed(Papeis.ADMIN)
public class RedmineService {

    private static final Logger LOG = Logger.getLogger(RedmineService.class.getName());

    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(30);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Inject
    private ConfiguracaoRedmineRepository configuracaoRepository;

    @Inject
    private UsuarioRepository usuarioRepository;

    @Resource
    private SessionContext sessionContext;

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public ConfiguracaoRedmineDTO buscarConfiguracao() {
        return configuracaoRepository.buscarDoUsuario(usuarioLogado().getId())
                .map(RedmineService::toDTO)
                .orElse(new ConfiguracaoRedmineDTO(false, null, null, null, null));
    }

    /**
     * O link precisa ser de uma lista de chamados do Redmine (é conferido aqui, para o erro aparecer ao salvar e
     * não só ao importar). Chave em branco mantém a gravada (o cliente não a conhece); na primeira vez, é obrigatória.
     */
    public ConfiguracaoRedmineDTO salvarConfiguracao(SalvarConfiguracaoRedmineRequest request) {
        String link = request.url().trim();
        String projeto = semBrancos(request.projeto());
        try {
            ConsultaRedmine.paginaJson(ConsultaRedmine.baseDaConsulta(link), projeto, link, 0);
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException("Configuração inválida", List.of(e.getMessage()));
        }
        Usuario usuario = usuarioLogado();
        ConfiguracaoRedmine configuracao = configuracaoRepository.buscarDoUsuario(usuario.getId()).orElse(null);
        String chave = semBrancos(request.chaveApi());
        if (chave == null && configuracao == null) {
            throw new DadosInvalidosException("Configuração inválida", List.of("Informe a chave da API do Redmine"));
        }
        if (configuracao == null) {
            configuracao = new ConfiguracaoRedmine(usuario);
            configuracao.alterar(link, projeto, chave);
            configuracaoRepository.salvar(configuracao);
        } else {
            configuracao.alterar(link, projeto, chave == null ? configuracao.getChaveApi() : chave);
        }
        return toDTO(configuracao);
    }

    /** Confere a chave ({@code /users/current.json}), o projeto (se configurado) e quantos chamados a consulta traz. */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public TesteRedmineDTO testarConexao() {
        ConfiguracaoRedmine configuracao = configuracao();
        String base = base(configuracao);
        String usuario = ler(() -> ConsultaRedmine.lerUsuarioAtual(
                buscar(configuracao, URI.create(base + "/users/current.json"))));
        String projeto = configuracao.getProjeto() == null ? null : ler(() -> ConsultaRedmine.lerProjeto(
                buscar(configuracao, URI.create(base + "/projects/"
                        + URLEncoder.encode(configuracao.getProjeto(), StandardCharsets.UTF_8) + ".json"))));
        int chamados = ler(() -> ConsultaRedmine.lerPagina(buscar(configuracao, pagina(configuracao, 0)))).total();
        return new TesteRedmineDTO(usuario, projeto, chamados);
    }

    /**
     * Todos os chamados da consulta configurada, página por página. Mais de {@link ImportacaoTarefas#MAXIMO} → 422,
     * antes de buscar o resto: a consulta precisa ser filtrada no Redmine.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<CriarTarefaRequest> buscarChamados() {
        ConfiguracaoRedmine configuracao = configuracao();
        List<CriarTarefaRequest> chamados = new ArrayList<>();
        int total;
        do {
            URI pagina = pagina(configuracao, chamados.size());
            ConsultaRedmine.Pagina resposta = ler(() -> ConsultaRedmine.lerPagina(buscar(configuracao, pagina)));
            total = resposta.total();
            if (total > ImportacaoTarefas.MAXIMO) {
                throw new NegocioException("A consulta tem " + total + " chamados, e o máximo por importação é "
                        + ImportacaoTarefas.MAXIMO + ". Filtre mais a consulta no Redmine.");
            }
            if (resposta.chamados().isEmpty()) {
                break;
            }
            chamados.addAll(resposta.chamados());
        } while (chamados.size() < total);

        if (chamados.isEmpty()) {
            throw new NegocioException("A consulta do Redmine não trouxe nenhum chamado");
        }
        return chamados;
    }

    private static URI pagina(ConfiguracaoRedmine configuracao, int offset) {
        return ler(() -> ConsultaRedmine.paginaJson(base(configuracao), configuracao.getProjeto(),
                configuracao.getUrl(), offset));
    }

    private static String base(ConfiguracaoRedmine configuracao) {
        return ler(() -> ConsultaRedmine.baseDaConsulta(configuracao.getUrl()));
    }

    private String buscar(ConfiguracaoRedmine configuracao, URI uri) {
        HttpRequest requisicao = HttpRequest.newBuilder(uri)
                .timeout(TEMPO_LIMITE)
                .header("X-Redmine-API-Key", configuracao.getChaveApi())
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resposta;
        try {
            resposta = HTTP.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warning(() -> "Falha ao acessar o Redmine (" + uri.getHost() + "): " + e);
            throw new NegocioException("Não foi possível acessar o Redmine em " + uri.getScheme() + "://" + uri.getAuthority()
                    + " (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NegocioException("A consulta ao Redmine foi interrompida");
        }
        return switch (resposta.statusCode()) {
            case 200 -> resposta.body();
            case 401 -> throw new NegocioException("O Redmine recusou a chave da API (401). Confira a chave na configuração "
                    + "e se a API REST está habilitada no Redmine (Administração → Configurações → API)");
            case 403 -> throw new NegocioException("Sem permissão no Redmine para essa consulta (403)");
            case 404 -> throw new NegocioException("Não encontrado no Redmine (404): confira o link da consulta e o projeto");
            case 422 -> throw new NegocioException("O Redmine recusou a consulta (422): confira os filtros do link");
            default -> throw new NegocioException("O Redmine respondeu com erro HTTP " + resposta.statusCode());
        };
    }

    /** As falhas de leitura do link ou do JSON viram 422 com a mensagem do {@link ConsultaRedmine}. */
    private static <T> T ler(Supplier<T> leitura) {
        try {
            return leitura.get();
        } catch (IllegalArgumentException e) {
            throw new NegocioException(e.getMessage());
        }
    }

    private ConfiguracaoRedmine configuracao() {
        return configuracaoRepository.buscarDoUsuario(usuarioLogado().getId())
                .orElseThrow(() -> new NegocioException("Configure o acesso ao Redmine antes de importar (botão ⚙ Redmine)"));
    }

    private Usuario usuarioLogado() {
        String nome = sessionContext.getCallerPrincipal().getName();
        return usuarioRepository.buscarPorUsuario(nome)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + nome));
    }

    private static ConfiguracaoRedmineDTO toDTO(ConfiguracaoRedmine configuracao) {
        String chave = configuracao.getChaveApi();
        String finalChave = chave.length() <= 4 ? chave : chave.substring(chave.length() - 4);
        return new ConfiguracaoRedmineDTO(true, configuracao.getUrl(), configuracao.getProjeto(), finalChave,
                configuracao.getAtualizadaEm());
    }

    private static String semBrancos(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
