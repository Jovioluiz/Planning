package com.planningapp.service.redmine;

import com.planningapp.dto.CriarTarefaRequest;
import org.junit.jupiter.api.Test;

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
        assertEquals(new CriarTarefaRequest("63897", "Segmento Operacional - DRE", "h2. Caso de Uso", "0.0.2",
                Map.of("Título", "Segmento Operacional - DRE", "Tipo", "Customização", "Situação", "Nova",
                        "Prioridade", "02-Urgente", "Atribuído para", "Felipe da Silva", "Versão", "0.0.2",
                        "Tempo estimado", "6.5")), pagina.chamados().get(0));
        CriarTarefaRequest semVersao = pagina.chamados().get(1);
        assertNull(semVersao.descricao());
        assertNull(semVersao.sprint());
        assertEquals(Map.of("Título", "Ajuste", "Tipo", "Bug"), semVersao.dadosExtras());
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
}
