package com.planningapp.service.redmine;

import com.planningapp.dto.CriarTarefaRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultaRedmineTest {

    private static final String BASE = "https://redmine.empresa.com.br";

    @Test
    void baseSaiDoLinkDaConsultaComOuSemProjeto() {
        assertEquals(BASE, ConsultaRedmine.baseDaConsulta(BASE + "/projects/erp/issues?query_id=123"));
        assertEquals(BASE, ConsultaRedmine.baseDaConsulta(BASE + "/issues?set_filter=1&f[]=status_id#topo"));
        assertEquals("http://intranet/redmine", ConsultaRedmine.baseDaConsulta("http://intranet/redmine/issues.csv/"));
    }

    @Test
    void linkQueNaoEhListaDeChamadosNaoTemBase() {
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.baseDaConsulta(BASE));
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.baseDaConsulta(BASE + "/issues/63897"));
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.baseDaConsulta("redmine/issues"));
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.baseDaConsulta(null));
    }

    @Test
    void normalizaBaseTirandoBarraDoFim() {
        assertEquals(BASE, ConsultaRedmine.normalizarBase(" https://redmine.empresa.com.br/ "));
        assertEquals("http://intranet/redmine", ConsultaRedmine.normalizarBase("http://intranet/redmine/"));
    }

    @Test
    void recusaBaseSemHttpOuComConsulta() {
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.normalizarBase("redmine.empresa.com.br"));
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.normalizarBase("ftp://redmine"));
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.normalizarBase(BASE + "/issues?query_id=1"));
    }

    @Test
    void consultaSalvaDoProjetoViraIssuesJsonComPaginacao() {
        assertEquals(BASE + "/projects/erp/issues.json?query_id=123&limit=100&offset=0",
                ConsultaRedmine.paginaJson(BASE, "outro", BASE + "/projects/erp/issues?query_id=123", 0).toString());
    }

    @Test
    void mantemFiltrosCodificandoColchetesETrocaAPaginacaoDoLink() {
        String link = BASE + "/issues?set_filter=1&f[]=status_id&op[status_id]=o&per_page=25&page=3&sort=id:desc#topo";

        assertEquals(BASE + "/issues.json?set_filter=1&f%5B%5D=status_id&op%5Bstatus_id%5D=o&sort=id:desc"
                        + "&project_id=erp&limit=100&offset=200",
                ConsultaRedmine.paginaJson(BASE, "erp", link, 200).toString());
    }

    @Test
    void linkSemProjetoNaoGanhaProjectIdQuandoJaTemOuNaoHaConfigurado() {
        assertEquals(BASE + "/issues.json?project_id=fiscal&limit=100&offset=0",
                ConsultaRedmine.paginaJson(BASE, "erp", BASE + "/issues?project_id=fiscal", 0).toString());
        assertEquals(BASE + "/issues.json?query_id=7&limit=100&offset=0",
                ConsultaRedmine.paginaJson(BASE, null, BASE + "/issues?query_id=7", 0).toString());
    }

    @Test
    void aceitaLinkDaExportacaoCsv() {
        assertEquals(BASE + "/projects/erp/issues.json?query_id=5&limit=100&offset=0",
                ConsultaRedmine.paginaJson(BASE, null, BASE + "/projects/erp/issues.csv?query_id=5", 0).toString());
    }

    @Test
    void recusaLinkDeOutroServidorOuQueNaoEhListaDeChamados() {
        IllegalArgumentException outroServidor = assertThrows(IllegalArgumentException.class,
                () -> ConsultaRedmine.paginaJson(BASE, null, "https://outro.com/issues?query_id=1", 0));
        assertTrue(outroServidor.getMessage().contains(BASE));
        assertThrows(IllegalArgumentException.class,
                () -> ConsultaRedmine.paginaJson(BASE, null, BASE + ".evil.com/issues", 0));
        assertThrows(IllegalArgumentException.class,
                () -> ConsultaRedmine.paginaJson(BASE, null, BASE + "/issues/63897", 0));
    }

    @Test
    void leChamadosComOsCamposDaExportacaoDoRedmine() {
        String json = """
                {"issues":[
                  {"id":63897,"subject":"Segmento Operacional - DRE","description":"h2. Caso de Uso",
                   "tracker":{"id":4,"name":"Customização"},"status":{"id":1,"name":"Nova"},
                   "priority":{"id":5,"name":"02-Urgente"},"assigned_to":{"id":9,"name":"Felipe da Silva"},
                   "fixed_version":{"id":3,"name":"0.0.2"},"estimated_hours":6.5},
                  {"id":63901,"subject":"Ajuste","description":null,"tracker":{"id":1,"name":"Bug"}}
                ],"total_count":150,"offset":0,"limit":100}""";

        ConsultaRedmine.Pagina pagina = ConsultaRedmine.lerPagina(json);

        assertEquals(150, pagina.total());
        CriarTarefaRequest primeiro = pagina.chamados().get(0);
        assertEquals("63897", primeiro.numero());
        assertEquals("Segmento Operacional - DRE", primeiro.titulo());
        assertEquals("h2. Caso de Uso", primeiro.descricao());
        assertEquals("0.0.2", primeiro.sprint());
        assertEquals("Customização", primeiro.dadosExtras().get("Tipo"));
        assertEquals("Felipe da Silva", primeiro.dadosExtras().get("Atribuído para"));
        assertEquals("6.5", primeiro.dadosExtras().get("Tempo estimado"));
        CriarTarefaRequest semVersao = pagina.chamados().get(1);
        assertNull(semVersao.descricao());
        assertNull(semVersao.sprint());
        assertEquals("Bug", semVersao.dadosExtras().get("Tipo"));
        assertEquals("", semVersao.dadosExtras().get("Versão"));
        assertEquals("", semVersao.dadosExtras().get("Atribuído para"));
    }

    @Test
    void leAnexosDoChamado() {
        List<ConsultaRedmine.Anexo> anexos = ConsultaRedmine.lerAnexos("""
                {"issue":{"id":63901,"attachments":[
                  {"id":501,"filename":"print tela.png","filesize":2048,"content_type":"image/png",
                   "content_url":"https://redmine.empresa.com.br/attachments/download/501/print%20tela.png"},
                  {"id":502,"filename":"log.txt","filesize":10}]}}""");

        assertEquals(List.of(new ConsultaRedmine.Anexo(501, "print tela.png", "image/png", 2048),
                new ConsultaRedmine.Anexo(502, "log.txt", null, 10)), anexos);
        assertEquals(List.of(), ConsultaRedmine.lerAnexos("{\"issue\":{\"id\":1}}"));
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.lerAnexos("<html>login</html>"));
    }

    @Test
    void leUmChamadoComoNaImportacao() {
        String chamado = "{\"id\":63901,\"subject\":\"Ajuste\",\"tracker\":{\"id\":1,\"name\":\"Bug\"},"
                + "\"custom_fields\":[{\"id\":91,\"name\":\"Data Inicio Homologação\",\"value\":null}]}";

        ConsultaRedmine.ChamadoLido lido = ConsultaRedmine.lerTarefa("{\"issue\":" + chamado + "}");

        assertEquals("63901", lido.tarefa().numero());
        assertEquals("Ajuste", lido.tarefa().titulo());
        assertEquals("", lido.tarefa().dadosExtras().get("Data Inicio Homologação"));
        assertEquals(chamado, lido.json());
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.lerTarefa("{\"errors\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.lerTarefa("<html>login</html>"));
    }

    @Test
    void guardaOJsonDeCadaChamadoComoVeioDoRedminePeloNumero() {
        String chamado = "{\"id\":63901,\"subject\":\"Ajuste\",\"description\":null,\"tracker\":{\"id\":1,\"name\":\"Bug\"},"
                + "\"custom_fields\":[{\"id\":11,\"name\":\"Planning\",\"value\":\"\"}]}";

        ConsultaRedmine.Pagina pagina = ConsultaRedmine.lerPagina("{\"issues\":[" + chamado + "],\"total_count\":1}");

        assertEquals(Map.of("63901", chamado), pagina.json());
        assertEquals(1, pagina.total());
    }

    @Test
    void gravaTodosOsCamposDoChamadoNaOrdemDoPainelDeDetalhe() {
        String json = """
                {"issues":[
                  {"id":63897,"subject":"Segmento Operacional - DRE","description":"h2. Caso de Uso",
                   "project":{"id":2,"name":"ERP"},"tracker":{"id":4,"name":"Customização"},
                   "status":{"id":1,"name":"Nova"},"priority":{"id":5,"name":"02-Urgente"},
                   "author":{"id":7,"name":"Ana Souza"},"assigned_to":{"id":9,"name":"Felipe da Silva"},
                   "category":{"id":3,"name":"Financeiro"},"fixed_version":{"id":3,"name":"0.0.2"},
                   "parent":{"id":63800},"start_date":"2025-10-14","due_date":null,"done_ratio":30,
                   "is_private":false,"estimated_hours":6.5,"total_estimated_hours":10.0,"spent_hours":2.0,
                   "custom_fields":[
                     {"id":11,"name":"Versão Merge","value":"0.0.2"},
                     {"id":12,"name":"Módulos","multiple":true,"value":["Fiscal","Estoque"]},
                     {"id":13,"name":"Cliente","value":""},
                     {"id":14,"name":"Tipo","value":"não sobrescreve o padrão"}],
                   "created_on":"2025-10-14T12:30:00Z","updated_on":"2025-10-20T18:05:00Z","closed_on":null}
                ]}""";

        Map<String, String> extras = ConsultaRedmine.lerPagina(json).chamados().get(0).dadosExtras();

        assertEquals(List.of("Título", "Tipo", "Situação", "Prioridade", "Atribuído para", "Versão", "Tempo estimado",
                        "Projeto", "Categoria", "Autor", "Tarefa pai", "Início", "Data prevista", "% Terminado",
                        "Tempo estimado total", "Tempo gasto", "Criada em", "Atualizada em", "Fechada em",
                        "Versão Merge", "Módulos", "Cliente"),
                List.copyOf(extras.keySet()));
        assertEquals("", extras.get("Data prevista"));
        assertEquals("", extras.get("Cliente"));
        assertEquals("Customização", extras.get("Tipo"));
        assertEquals("#63800", extras.get("Tarefa pai"));
        assertEquals("14/10/2025", extras.get("Início"));
        assertEquals("30%", extras.get("% Terminado"));
        assertEquals("10.0", extras.get("Tempo estimado total"));
        assertEquals("14/10/2025 09:30", extras.get("Criada em"));
        assertEquals("Fiscal, Estoque", extras.get("Módulos"));
    }

    @Test
    void cortaVersaoLongaNoTamanhoDaSprint() {
        String versao = "v".repeat(80);
        String json = "{\"issues\":[{\"id\":1,\"subject\":\"x\",\"fixed_version\":{\"name\":\"" + versao + "\"}}]}";

        CriarTarefaRequest chamado = ConsultaRedmine.lerPagina(json).chamados().get(0);

        assertEquals(50, chamado.sprint().length());
        assertEquals(versao, chamado.dadosExtras().get("Versão"));
    }

    @Test
    void respostaQueNaoEhListaDeChamadosEhRecusada() {
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.lerPagina("<html>login</html>"));
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.lerPagina("{\"errors\":[\"x\"]}"));
    }

    @Test
    void leUsuarioDaChaveEProjeto() {
        assertEquals("Jóvio Giacomolli", ConsultaRedmine.lerUsuarioAtual(
                "{\"user\":{\"login\":\"jovio\",\"firstname\":\"Jóvio\",\"lastname\":\"Giacomolli\"}}"));
        assertEquals("jovio", ConsultaRedmine.lerUsuarioAtual("{\"user\":{\"login\":\"jovio\"}}"));
        assertEquals("ERP", ConsultaRedmine.lerProjeto("{\"project\":{\"id\":1,\"name\":\"ERP\"}}"));
    }

    @Test
    void leChamadoEAchaCampoPersonalizadoPeloId() {
        ConsultaRedmine.Chamado chamado = ConsultaRedmine.lerChamado("""
                {"issue":{"id":63897,"estimated_hours":6.5,"status":{"id":55,"name":"Produto Backlog "},
                 "custom_fields":[
                  {"id":27,"name":"Planning","value":""},
                  {"id":66,"name":"EstimativaTeste","value":"3"},
                  {"id":91,"name":"Data Inicio Homologação","value":null},
                  {"id":13,"name":"Módulos","multiple":true,"value":["Fiscal","Estoque"]}]}}""");

        assertEquals(6.5, chamado.tempoEstimado());
        assertEquals(new ConsultaRedmine.Situacao(55, "Produto Backlog"), chamado.situacao());
        assertEquals("Planning", chamado.campo(27).nome());
        assertEquals("", chamado.campo(27).valor());
        assertEquals("3", chamado.campo(66).valor());
        assertNull(chamado.campo(91).valor());
        assertEquals("Fiscal, Estoque", chamado.campo(13).valor());
        assertNull(chamado.campo(999));
        ConsultaRedmine.Chamado vazio = ConsultaRedmine.lerChamado("{\"issue\":{\"id\":1}}");
        assertNull(vazio.tempoEstimado());
        assertNull(vazio.situacao());
        assertThrows(IllegalArgumentException.class, () -> ConsultaRedmine.lerChamado("<html>login</html>"));
    }

    @Test
    void montaCorpoDaAtualizacaoDoChamado() {
        Map<Integer, String> campos = new java.util.LinkedHashMap<>();
        campos.put(27, "8");
        campos.put(66, "3");

        assertEquals("{\"issue\":{\"estimated_hours\":5,\"status_id\":8,\"custom_fields\":"
                        + "[{\"id\":27,\"value\":\"8\"},{\"id\":66,\"value\":\"3\"}]}}",
                ConsultaRedmine.corpoAtualizacao(5, 8, campos));
        assertEquals("{\"issue\":{\"custom_fields\":[{\"id\":27,\"value\":\"8\"}]}}",
                ConsultaRedmine.corpoAtualizacao(null, null, Map.of(27, "8")));
        assertEquals("{\"issue\":{\"estimated_hours\":5}}", ConsultaRedmine.corpoAtualizacao(5, null, Map.of()));
    }

    @Test
    void leErrosDaRecusaDoRedmine() {
        assertEquals(List.of("Tempo estimado não é um número", "Planning não pode ficar vazio"),
                ConsultaRedmine.lerErros("{\"errors\":[\"Tempo estimado não é um número\",\"Planning não pode ficar vazio\"]}"));
        assertEquals(List.of(), ConsultaRedmine.lerErros("<html>erro</html>"));
        assertEquals(List.of(), ConsultaRedmine.lerErros(null));
    }
}
