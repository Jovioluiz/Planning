package com.planningapp.service;

import com.planningapp.dto.ConfiguracaoRedmineDTO;
import com.planningapp.dto.CriarTarefaRequest;
import com.planningapp.dto.EnvioRedmineDTO;
import com.planningapp.dto.SalvarConfiguracaoRedmineRequest;
import com.planningapp.dto.TesteRedmineDTO;
import com.planningapp.model.ConfiguracaoRedmine;
import com.planningapp.model.Usuario;
import com.planningapp.persistence.ConfiguracaoRedmineRepository;
import com.planningapp.persistence.UsuarioRepository;
import com.planningapp.security.CifraSegredos;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Integração com o Redmine: a configuração de acesso de cada moderador (link da consulta, projeto e chave da API)
 * e a busca dos chamados dessa consulta, para a importação de tarefas ({@link TarefaService#importarRedmine}).
 *
 * <p>A chave fica no banco cifrada ({@link CifraSegredos}) e nunca volta para o cliente; só é decifrada para
 * chamar o Redmine. O Redmine aplica as permissões de quem gerou a chave.</p>
 */
@Stateless
@RolesAllowed(Papeis.ADMIN)
public class RedmineService {

    private static final Logger LOG = Logger.getLogger(RedmineService.class.getName());

    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(30);

    private static final long MAXIMO_ANEXO = 10L * 1024 * 1024;

    // Ids do Redmine da equipe que recebem a estimativa final (ver enviarEstimativa)
    private static final int CAMPO_PLANNING = 27;
    private static final int CAMPO_ESTIMATIVA_TESTE = 66;
    private static final int SITUACAO_PRODUTO_BACKLOG = 55;
    private static final int SITUACAO_AGUARDANDO_DESENVOLVIMENTO = 8;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Inject
    private ConfiguracaoRedmineRepository configuracaoRepository;

    @Inject
    private UsuarioRepository usuarioRepository;

    @Inject
    private CifraSegredos cifra;

    @Resource
    private SessionContext sessionContext;

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public ConfiguracaoRedmineDTO buscarConfiguracao() {
        return configuracaoRepository.buscarDoUsuario(usuarioLogado().getId())
                .map(this::toDTO)
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
            configuracao.alterar(link, projeto, cifra.cifrar(chave));
            configuracaoRepository.salvar(configuracao);
        } else {
            String gravada = configuracao.getChaveApi();
            String chaveCifrada = chave != null ? cifra.cifrar(chave)
                    : cifra.estaCifrado(gravada) ? gravada : cifra.cifrar(gravada);
            configuracao.alterar(link, projeto, chaveCifrada);
        }
        return toDTO(configuracao);
    }

    /**
     * Grava a estimativa final no chamado: as horas no "Tempo estimado", os pontos no campo "Planning" (27), as
     * horas de teste no "EstimativaTeste" (66), e muda a situação de "Produto Backlog" (55) para "Aguardando
     * Desenvolvimento" (8), tudo num só PUT. Chamado em outra situação não muda: já andou no fluxo. Depois relê o chamado para conferir, porque o Redmine responde sucesso e ignora em silêncio o
     * campo que o usuário da chave não pode alterar (permissão do papel ou campo somente leitura no fluxo de
     * trabalho) e a transição de situação que o fluxo não permite.
     *
     * <p>Falha de acesso (rede, chave, chamado inexistente) sobe como {@link NegocioException}; o que só não foi
     * gravado volta nos avisos. Quem chama já finalizou a tarefa: o envio nunca a desfaz.</p>
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public EnvioRedmineDTO enviarEstimativa(String numero, Integer pontos, Integer horas, Integer horasTeste) {
        ConfiguracaoRedmine configuracao = configuracao();
        if (!numero.matches("\\d+")) {
            throw new NegocioException("O número " + numero + " não é de um chamado do Redmine");
        }
        URI uri = URI.create(base(configuracao) + "/issues/" + numero + ".json");
        ConsultaRedmine.Chamado antes = ler(() -> ConsultaRedmine.lerChamado(buscar(configuracao, uri)));

        List<String> avisos = new ArrayList<>();
        Map<Integer, String> personalizados = new LinkedHashMap<>();
        Map<ConsultaRedmine.CampoPersonalizado, String> enviados = new LinkedHashMap<>();
        incluirCampo(antes, CAMPO_PLANNING, "Planning", pontos, personalizados, enviados, avisos);
        incluirCampo(antes, CAMPO_ESTIMATIVA_TESTE, "EstimativaTeste", horasTeste, personalizados, enviados, avisos);
        boolean mudarSituacao = mudarSituacao(antes, avisos);
        if (horas == null && personalizados.isEmpty() && !mudarSituacao) {
            avisos.add("Nada a enviar: a tarefa não tem horas nem pontos");
            return new EnvioRedmineDTO(numero, List.of(), avisos);
        }

        requisitar(configuracao, uri, "PUT", ConsultaRedmine.corpoAtualizacao(horas,
                mudarSituacao ? SITUACAO_AGUARDANDO_DESENVOLVIMENTO : null, personalizados));

        ConsultaRedmine.Chamado depois = ler(() -> ConsultaRedmine.lerChamado(buscar(configuracao, uri)));
        List<String> gravados = new ArrayList<>();
        if (horas != null) {
            if (depois.tempoEstimado() != null && depois.tempoEstimado() == horas.doubleValue()) {
                gravados.add("Tempo estimado: " + horas + "h");
            } else {
                avisos.add("O Redmine não gravou o Tempo estimado (sem permissão para alterá-lo nesse chamado?)");
            }
        }
        enviados.forEach((campo, valor) -> {
            ConsultaRedmine.CampoPersonalizado gravado = depois.campo(campo.id());
            if (gravado != null && valor.equals(gravado.valor())) {
                gravados.add(campo.nome() + ": " + valor);
            } else {
                avisos.add("O Redmine não gravou o campo \"" + campo.nome()
                        + "\" (sem permissão ou somente leitura nessa situação do chamado?)");
            }
        });
        if (mudarSituacao) {
            if (depois.situacao() != null && depois.situacao().id() == SITUACAO_AGUARDANDO_DESENVOLVIMENTO) {
                gravados.add("Situação: " + depois.situacao().nome());
            } else {
                avisos.add("O Redmine não mudou a situação para \"Aguardando Desenvolvimento\""
                        + (depois.situacao() == null ? "" : " (ficou \"" + depois.situacao().nome() + "\")")
                        + ": transição não permitida pelo fluxo de trabalho?");
            }
        }
        return new EnvioRedmineDTO(numero, gravados, avisos);
    }

    /** Só sai de "Produto Backlog": em qualquer outra situação o chamado já andou no fluxo e não volta. */
    private static boolean mudarSituacao(ConsultaRedmine.Chamado chamado, List<String> avisos) {
        if (chamado.situacao() == null || chamado.situacao().id() == SITUACAO_AGUARDANDO_DESENVOLVIMENTO) {
            return false;
        }
        if (chamado.situacao().id() != SITUACAO_PRODUTO_BACKLOG) {
            avisos.add("Situação não alterada: o chamado está em \"" + chamado.situacao().nome()
                    + "\", e só muda de \"Produto Backlog\"");
            return false;
        }
        return true;
    }

    /** Se o campo não existir no chamado (não vale para esse tipo), avisa em vez de falhar. */
    private static void incluirCampo(ConsultaRedmine.Chamado chamado, int id, String nome, Integer valor,
                                     Map<Integer, String> personalizados,
                                     Map<ConsultaRedmine.CampoPersonalizado, String> enviados, List<String> avisos) {
        if (valor == null) {
            return;
        }
        ConsultaRedmine.CampoPersonalizado campo = chamado.campo(id);
        if (campo == null) {
            avisos.add("O chamado não tem o campo \"" + nome + "\" (id " + id + "): ele não vale para esse tipo de chamado?");
            return;
        }
        personalizados.put(campo.id(), valor.toString());
        enviados.put(campo, valor.toString());
    }

    /** O chamado como está agora no Redmine, para atualizar a tarefa ({@link TarefaService#atualizarDoRedmine}). */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public ConsultaRedmine.ChamadoLido buscarChamado(String numero) {
        ConfiguracaoRedmine configuracao = configuracao();
        if (!numero.matches("\\d+")) {
            throw new NegocioException("O número " + numero + " não é de um chamado do Redmine");
        }
        URI uri = URI.create(base(configuracao) + "/issues/" + numero + ".json");
        return ler(() -> ConsultaRedmine.lerTarefa(buscar(configuracao, uri)));
    }

    /** Arquivo baixado do Redmine: o tipo vai no {@code Content-Type} da resposta. */
    public record Arquivo(String tipo, byte[] conteudo) {
    }

    /**
     * Imagem anexada ao chamado, pelo nome do arquivo (o que a descrição cita em {@code !print.png!}), para o painel
     * de detalhe mostrá-la: o download exige a chave, que não sai do servidor. Só imagens (sem SVG, que pode ter
     * script) de até {@link #MAXIMO_ANEXO} bytes, e sempre do Redmine configurado: o endereço é montado aqui com o
     * id do anexo, nunca o {@code content_url} da resposta.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Arquivo baixarAnexo(String numero, String nome) {
        ConfiguracaoRedmine configuracao = configuracao();
        if (!numero.matches("\\d+")) {
            throw new NegocioException("O número " + numero + " não é de um chamado do Redmine");
        }
        String base = base(configuracao);
        List<ConsultaRedmine.Anexo> anexos = ler(() -> ConsultaRedmine.lerAnexos(
                buscar(configuracao, URI.create(base + "/issues/" + numero + ".json?include=attachments"))));
        ConsultaRedmine.Anexo anexo = anexos.stream().filter(a -> a.nome().equals(nome)).findFirst()
                .or(() -> anexos.stream().filter(a -> a.nome().equalsIgnoreCase(nome)).findFirst())
                .orElseThrow(() -> new RecursoNaoEncontradoException("O chamado " + numero + " não tem o anexo " + nome));
        String tipo = anexo.tipo() == null ? "" : anexo.tipo().toLowerCase(java.util.Locale.ROOT);
        if (!tipo.startsWith("image/") || tipo.contains("svg")) {
            throw new NegocioException("O anexo " + nome + " não é uma imagem");
        }
        if (anexo.tamanho() > MAXIMO_ANEXO) {
            throw new NegocioException("O anexo " + nome + " é grande demais para mostrar (mais de 10 MB)");
        }
        byte[] conteudo = requisitar(configuracao, URI.create(base + "/attachments/download/" + anexo.id()), "GET",
                null, HttpResponse.BodyHandlers.ofByteArray());
        if (conteudo.length > MAXIMO_ANEXO) {
            throw new NegocioException("O anexo " + nome + " é grande demais para mostrar (mais de 10 MB)");
        }
        return new Arquivo(tipo, conteudo);
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
     * Todos os chamados da consulta configurada, página por página, com o JSON de cada um como veio do Redmine.
     * Mais de {@link ImportacaoTarefas#MAXIMO} → 422, antes de buscar o resto: a consulta precisa ser filtrada no
     * Redmine.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public ConsultaRedmine.Pagina buscarChamados() {
        ConfiguracaoRedmine configuracao = configuracao();
        List<CriarTarefaRequest> chamados = new ArrayList<>();
        Map<String, String> json = new LinkedHashMap<>();
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
            json.putAll(resposta.json());
        } while (chamados.size() < total);

        if (chamados.isEmpty()) {
            throw new NegocioException("A consulta do Redmine não trouxe nenhum chamado");
        }
        return new ConsultaRedmine.Pagina(chamados, json, chamados.size());
    }

    private static URI pagina(ConfiguracaoRedmine configuracao, int offset) {
        return ler(() -> ConsultaRedmine.paginaJson(base(configuracao), configuracao.getProjeto(),
                configuracao.getUrl(), offset));
    }

    private static String base(ConfiguracaoRedmine configuracao) {
        return ler(() -> ConsultaRedmine.baseDaConsulta(configuracao.getUrl()));
    }

    private String buscar(ConfiguracaoRedmine configuracao, URI uri) {
        return requisitar(configuracao, uri, "GET", null);
    }

    /** {@code corpo} nulo: sem corpo (GET). A atualização ({@code PUT}) responde 204, sem corpo. */
    private String requisitar(ConfiguracaoRedmine configuracao, URI uri, String metodo, String corpo) {
        return requisitar(configuracao, uri, metodo, corpo, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private <T> T requisitar(ConfiguracaoRedmine configuracao, URI uri, String metodo, String corpo,
                             HttpResponse.BodyHandler<T> leitor) {
        HttpRequest requisicao = HttpRequest.newBuilder(uri)
                .timeout(TEMPO_LIMITE)
                .header("X-Redmine-API-Key", chaveApi(configuracao))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();
        HttpResponse<T> resposta;
        try {
            resposta = HTTP.send(requisicao, leitor);
        } catch (IOException e) {
            LOG.warning(() -> "Falha ao acessar o Redmine (" + uri.getHost() + "): " + e);
            throw new NegocioException("Não foi possível acessar o Redmine em " + uri.getScheme() + "://" + uri.getAuthority()
                    + " (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NegocioException("A consulta ao Redmine foi interrompida");
        }
        boolean atualizacao = !"GET".equals(metodo);
        return switch (resposta.statusCode()) {
            case 200, 201, 204 -> resposta.body();
            case 401 -> throw new NegocioException("O Redmine recusou a chave da API (401). Confira a chave na configuração "
                    + "e se a API REST está habilitada no Redmine (Administração → Configurações → API)");
            case 403 -> throw new NegocioException(atualizacao
                    ? "Sem permissão no Redmine para editar esse chamado (403)"
                    : "Sem permissão no Redmine para essa consulta (403)");
            case 404 -> throw new NegocioException(atualizacao || uri.getPath().contains("/issues/")
                    ? "Chamado não encontrado no Redmine (404), ou sem permissão para vê-lo"
                    : "Não encontrado no Redmine (404): confira o link da consulta e o projeto");
            case 422 -> {
                List<String> erros = resposta.body() instanceof String texto ? ConsultaRedmine.lerErros(texto) : List.of();
                throw new NegocioException(atualizacao
                        ? "O Redmine recusou a atualização (422)" + (erros.isEmpty() ? "" : ": " + String.join("; ", erros))
                        : "O Redmine recusou a consulta (422): confira os filtros do link");
            }
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

    /** A chave em texto puro, só para o cabeçalho da requisição ao Redmine. */
    private String chaveApi(ConfiguracaoRedmine configuracao) {
        try {
            return cifra.decifrar(configuracao.getChaveApi());
        } catch (CifraSegredos.SegredoIlegivelException e) {
            throw new NegocioException("Não foi possível ler a chave da API gravada (o segredo do servidor mudou?). "
                    + "Informe a chave do Redmine de novo e salve.");
        }
    }

    private ConfiguracaoRedmineDTO toDTO(ConfiguracaoRedmine configuracao) {
        String chave;
        try {
            chave = cifra.decifrar(configuracao.getChaveApi());
        } catch (CifraSegredos.SegredoIlegivelException e) {
            chave = "";
        }
        String finalChave = chave.length() <= 4 ? chave : chave.substring(chave.length() - 4);
        return new ConfiguracaoRedmineDTO(true, configuracao.getUrl(), configuracao.getProjeto(), finalChave,
                configuracao.getAtualizadaEm());
    }

    private static String semBrancos(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
