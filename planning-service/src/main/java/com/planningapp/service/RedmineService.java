package com.planningapp.service;

import com.planningapp.dto.ConfiguracaoRedmineDTO;
import com.planningapp.dto.CriarTarefaRequest;
import com.planningapp.dto.DadosEnvioRedmineDTO;
import com.planningapp.dto.EnviarRedmineRequest;
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
    private static final int CAMPO_VERSAO_MERGE = 28;
    private static final int CAMPO_PROJETO_TRABALHO = 56;
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
     * Desenvolvimento" (8), tudo num só PUT. Junto vão os campos confirmados no modal de finalização
     * ({@code complemento}: versão do merge, prioridade, data prevista e projeto de trabalho), só os que mudaram. Chamado em outra situação não muda: já andou no fluxo. Depois relê o chamado para conferir, porque o Redmine responde sucesso e ignora em silêncio o
     * campo que o usuário da chave não pode alterar (permissão do papel ou campo somente leitura no fluxo de
     * trabalho) e a transição de situação que o fluxo não permite.
     *
     * <p>Falha de acesso (rede, chave, chamado inexistente) sobe como {@link NegocioException}; o que só não foi
     * gravado volta nos avisos. Quem chama já finalizou a tarefa: o envio nunca a desfaz.</p>
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public EnvioRedmineDTO enviarEstimativa(String numero, Integer pontos, Integer horas, Integer horasTeste,
                                            EnviarRedmineRequest complemento) {
        ConfiguracaoRedmine configuracao = configuracao();
        URI uri = uriDoChamado(configuracao, numero);
        ConsultaRedmine.Chamado antes = ler(() -> ConsultaRedmine.lerChamado(buscar(configuracao, uri)));
        EnviarRedmineRequest extras = complemento != null ? complemento : new EnviarRedmineRequest(null, null, null, null);

        List<String> avisos = new ArrayList<>();
        Map<Integer, String> personalizados = new LinkedHashMap<>();
        Map<ConsultaRedmine.CampoPersonalizado, String> enviados = new LinkedHashMap<>();
        incluirCampo(antes, CAMPO_PLANNING, "Planning", texto(pontos), personalizados, enviados, avisos);
        incluirCampo(antes, CAMPO_ESTIMATIVA_TESTE, "EstimativaTeste", texto(horasTeste), personalizados, enviados, avisos);
        // Os campos do modal só vão se mudaram: reenviar o mesmo valor geraria aviso falso em campo sem permissão
        incluirSeMudou(antes, CAMPO_VERSAO_MERGE, "Versão Merge", extras.versaoMerge(), personalizados, enviados, avisos);
        incluirSeMudou(antes, CAMPO_PROJETO_TRABALHO, "Projeto de Trabalho", extras.projetoTrabalho(),
                personalizados, enviados, avisos);
        Integer prioridade = extras.prioridadeId() != null
                && (antes.prioridade() == null || antes.prioridade().id() != extras.prioridadeId())
                ? extras.prioridadeId() : null;
        String dataPrevista = extras.dataPrevista() == null ? null : extras.dataPrevista().trim();
        if (dataPrevista != null && dataPrevista.equals(antes.dataPrevista() == null ? "" : antes.dataPrevista())) {
            dataPrevista = null;
        }
        boolean mudarSituacao = mudarSituacao(antes, avisos);
        if (horas == null && personalizados.isEmpty() && !mudarSituacao && prioridade == null && dataPrevista == null) {
            avisos.add("Nada a enviar: a tarefa não tem horas nem pontos");
            return new EnvioRedmineDTO(numero, List.of(), avisos);
        }

        requisitar(configuracao, uri, "PUT", ConsultaRedmine.corpoAtualizacao(horas,
                mudarSituacao ? SITUACAO_AGUARDANDO_DESENVOLVIMENTO : null, prioridade, dataPrevista, personalizados));

        ConsultaRedmine.Chamado depois = ler(() -> ConsultaRedmine.lerChamado(buscar(configuracao, uri)));
        List<String> gravados = new ArrayList<>();
        if (horas != null) {
            if (depois.tempoEstimado() != null && depois.tempoEstimado() == horas.doubleValue()) {
                gravados.add("Tempo estimado: " + horas + "h");
            } else {
                avisos.add("O Redmine não gravou o Tempo estimado (sem permissão para alterá-lo nesse chamado?)");
            }
        }
        if (prioridade != null) {
            if (depois.prioridade() != null && depois.prioridade().id() == prioridade) {
                gravados.add("Prioridade: " + depois.prioridade().nome());
            } else {
                avisos.add("O Redmine não gravou a Prioridade (sem permissão para alterá-la nesse chamado?)");
            }
        }
        if (dataPrevista != null) {
            String gravada = depois.dataPrevista() == null ? "" : depois.dataPrevista();
            if (gravada.equals(dataPrevista)) {
                gravados.add("Data prevista: " + (gravada.isEmpty() ? "(vazia)" : formatarData(gravada)));
            } else {
                avisos.add("O Redmine não gravou a Data prevista (sem permissão para alterá-la nesse chamado?)");
            }
        }
        enviados.forEach((campo, valor) -> {
            ConsultaRedmine.CampoPersonalizado gravado = depois.campo(campo.id());
            String valorGravado = gravado == null || gravado.valor() == null ? "" : gravado.valor();
            if (gravado != null && valor.equals(valorGravado)) {
                gravados.add(campo.nome() + ": " + (valor.isEmpty() ? "(vazio)" : valor));
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
    private static void incluirCampo(ConsultaRedmine.Chamado chamado, int id, String nome, String valor,
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
        personalizados.put(campo.id(), valor);
        enviados.put(campo, valor);
    }

    /** Texto informado no modal: nulo não mexe; igual ao do chamado (vazio = nulo) não é enviado. */
    private static void incluirSeMudou(ConsultaRedmine.Chamado chamado, int id, String nome, String valor,
                                       Map<Integer, String> personalizados,
                                       Map<ConsultaRedmine.CampoPersonalizado, String> enviados, List<String> avisos) {
        if (valor == null) {
            return;
        }
        String novo = valor.trim();
        ConsultaRedmine.CampoPersonalizado atual = chamado.campo(id);
        String anterior = atual == null || atual.valor() == null ? "" : atual.valor();
        if (atual != null && novo.equals(anterior)) {
            return;
        }
        if (atual == null && novo.isEmpty()) {
            return;
        }
        incluirCampo(chamado, id, nome, novo, personalizados, enviados, avisos);
    }

    private static String texto(Integer valor) {
        return valor == null ? null : valor.toString();
    }

    /** {@code 2025-10-14} → {@code 14/10/2025}. */
    private static String formatarData(String data) {
        try {
            return java.time.LocalDate.parse(data).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (java.time.format.DateTimeParseException e) {
            return data;
        }
    }

    /**
     * Os valores atuais do chamado, para o modal de finalização vir preenchido, com as prioridades do Redmine e os
     * valores de "Projeto de Trabalho" e "Versão Merge" (da definição dos campos ou, sem permissão, os já usados nos
     * chamados do projeto). As listas são um extra: se o Redmine negar (a definição dos campos
     * personalizados é só para administradores), vêm vazias e o modal aceita o valor digitado.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public DadosEnvioRedmineDTO dadosParaEnvio(String numero) {
        ConfiguracaoRedmine configuracao = configuracao();
        String base = base(configuracao);
        ConsultaRedmine.Chamado chamado = ler(() -> ConsultaRedmine.lerChamado(
                buscar(configuracao, uriDoChamado(configuracao, numero))));

        List<DadosEnvioRedmineDTO.OpcaoRedmineDTO> prioridades = opcional(() -> ConsultaRedmine.lerPrioridades(
                buscar(configuracao, URI.create(base + "/enumerations/issue_priorities.json"))))
                .stream().map(p -> new DadosEnvioRedmineDTO.OpcaoRedmineDTO(p.id(), p.nome())).toList();
        // Campo de lista: a definição traz todos os valores, mas só uma chave de administrador do Redmine a lê
        String definicoes = opcionalTexto(() -> buscar(configuracao, URI.create(base + "/custom_fields.json")));
        List<String> projetos = definicoes == null ? List.of()
                : opcional(() -> ConsultaRedmine.lerValoresPossiveis(definicoes, CAMPO_PROJETO_TRABALHO));
        List<String> versoes = definicoes == null ? List.of()
                : opcional(() -> ConsultaRedmine.lerValoresPossiveis(definicoes, CAMPO_VERSAO_MERGE));
        if (projetos.isEmpty() || versoes.isEmpty()) {
            Map<Integer, java.util.Set<String>> usados = valoresUsados(configuracao, base, chamado.projetoId());
            if (projetos.isEmpty()) {
                projetos = usados.get(CAMPO_PROJETO_TRABALHO).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            }
            if (versoes.isEmpty()) {
                versoes = usados.get(CAMPO_VERSAO_MERGE).stream().toList();
            }
        }
        versoes = ConsultaRedmine.ordenarVersoes(versoes);

        return new DadosEnvioRedmineDTO(
                inteiro(valorDoCampo(chamado, CAMPO_PLANNING)),
                chamado.tempoEstimado(),
                inteiro(valorDoCampo(chamado, CAMPO_ESTIMATIVA_TESTE)),
                valorDoCampo(chamado, CAMPO_VERSAO_MERGE),
                chamado.prioridade() == null ? null : chamado.prioridade().id(),
                chamado.dataPrevista(),
                valorDoCampo(chamado, CAMPO_PROJETO_TRABALHO),
                prioridades, projetos, versoes);
    }

    /** Quantos chamados (os atualizados mais recentemente) são lidos para achar os valores usados nos campos. */
    private static final int CHAMADOS_PARA_VALORES = 500;

    /**
     * Versões do merge e projetos de trabalho já preenchidos nos chamados do projeto (todas as situações, os
     * {@link #CHAMADOS_PARA_VALORES} atualizados mais recentemente), para quando a chave não pode ler a definição
     * dos campos. Falha no meio: fica com o que já leu.
     */
    private Map<Integer, java.util.Set<String>> valoresUsados(ConfiguracaoRedmine configuracao, String base,
                                                              Integer projetoId) {
        java.util.Set<Integer> ids = java.util.Set.of(CAMPO_VERSAO_MERGE, CAMPO_PROJETO_TRABALHO);
        Map<Integer, java.util.Set<String>> valores = new LinkedHashMap<>();
        ids.forEach(id -> valores.put(id, new java.util.LinkedHashSet<>()));
        String filtro = "status_id=*&sort=updated_on:desc&limit=" + ConsultaRedmine.POR_PAGINA
                + (projetoId == null ? "" : "&project_id=" + projetoId);
        try {
            for (int offset = 0; offset < CHAMADOS_PARA_VALORES; offset += ConsultaRedmine.POR_PAGINA) {
                URI pagina = URI.create(base + "/issues.json?" + filtro + "&offset=" + offset);
                ConsultaRedmine.ValoresUsados lidos = ler(() -> ConsultaRedmine.lerValoresUsados(
                        buscar(configuracao, pagina), ids));
                lidos.valores().forEach((id, usados) -> valores.get(id).addAll(usados));
                if (lidos.lidos() == 0 || offset + lidos.lidos() >= lidos.total()) {
                    break;
                }
            }
        } catch (NegocioException e) {
            LOG.fine(() -> "Valores usados nos chamados indisponíveis: " + e.getMessage());
        }
        return valores;
    }

    private static String opcionalTexto(Supplier<String> busca) {
        try {
            return busca.get();
        } catch (NegocioException e) {
            return null;
        }
    }

    /** Lista auxiliar: uma falha (sem permissão, Redmine antigo) vira lista vazia em vez de impedir o modal. */
    private static <T> List<T> opcional(Supplier<List<T>> busca) {
        try {
            return busca.get();
        } catch (NegocioException | IllegalArgumentException e) {
            LOG.fine(() -> "Lista auxiliar do Redmine indisponível: " + e.getMessage());
            return List.of();
        }
    }

    private static String valorDoCampo(ConsultaRedmine.Chamado chamado, int id) {
        ConsultaRedmine.CampoPersonalizado campo = chamado.campo(id);
        return campo == null || campo.valor() == null || campo.valor().isBlank() ? null : campo.valor().trim();
    }

    /** "5", "5.0" → 5; vazio ou não numérico → nulo. */
    private static Integer inteiro(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(valor.replace(',', '.')));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static URI uriDoChamado(ConfiguracaoRedmine configuracao, String numero) {
        if (!numero.matches("\\d+")) {
            throw new NegocioException("O número " + numero + " não é de um chamado do Redmine");
        }
        return URI.create(base(configuracao) + "/issues/" + numero + ".json");
    }

    /** O chamado como está agora no Redmine, para atualizar a tarefa ({@link TarefaService#atualizarDoRedmine}). */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public ConsultaRedmine.ChamadoLido buscarChamado(String numero) {
        ConfiguracaoRedmine configuracao = configuracao();
        URI uri = uriDoChamado(configuracao, numero);
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
