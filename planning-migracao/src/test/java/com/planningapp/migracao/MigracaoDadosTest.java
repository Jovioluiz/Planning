package com.planningapp.migracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Migra um banco no formato do Planning original (H2) para um banco desta versão (H2 com o changelog). */
class MigracaoDadosTest {

    private Connection origem;
    private Connection destino;

    @BeforeEach
    void criarBancos() throws Exception {
        origem = DriverManager.getConnection("jdbc:h2:mem:origem-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        destino = DriverManager.getConnection("jdbc:h2:mem:destino-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        executarScript(origem, "/planning-original.sql");
    }

    @Test
    void migraTudoEContaNoRelatorio() throws Exception {
        Relatorio relatorio = MigracaoDados.migrar(origem, destino, MigracaoDados.Opcoes.padrao());

        assertEquals(5, relatorio.migrados("usuarios"));
        assertEquals(2, relatorio.migrados("salas"), "a do original e a criada para o backlog legado");
        assertEquals(5, relatorio.migrados("tarefas"));
        assertEquals(6, relatorio.migrados("estimativas"), "7 na origem, um repetido");
        assertEquals(3, relatorio.migrados("participantes_tarefa"), "4 na origem, um órfão");
        assertEquals(3, relatorio.migrados("sala_membros"));

        String avisos = String.join("\n", relatorio.avisos());
        assertTrue(avisos.contains(MigracaoDados.NOME_SALA_LEGADO), avisos);
        assertTrue(avisos.contains("repetido"), avisos);
        assertTrue(avisos.contains("participante(s) ignorado(s)"), avisos);
        assertTrue(avisos.contains("user_sprints"), avisos);
        assertTrue(avisos.contains("Tarefa 103: dados_extras"), avisos);
    }

    @Test
    void flagsViramStatusEFase() throws Exception {
        MigracaoDados.migrar(origem, destino, MigracaoDados.Opcoes.padrao());

        assertEquals(Arrays.asList("ESTIMADA", null, null), linha("SELECT status, fase, fase_horas_teste FROM tarefas WHERE id = 100"));
        assertEquals(Arrays.asList("PULADA", null, null), linha("SELECT status, fase, fase_horas_teste FROM tarefas WHERE id = 101"));
        assertEquals(List.of("LIBERADA", "HORAS", "VOTACAO"), linha("SELECT status, fase, fase_horas_teste FROM tarefas WHERE id = 102"));
        assertEquals(List.of("FILA"), linha("SELECT status FROM tarefas WHERE id = 103"));
    }

    @Test
    void valoresSaoConvertidos() throws Exception {
        MigracaoDados.migrar(origem, destino, MigracaoDados.Opcoes.padrao());

        // numero Long -> texto; horas Double -> inteiro; estimada_em -> encerrada_em
        assertEquals(List.of("1", "6", "3", "2"), linha("SELECT numero, horas_finais, horas_teste_finais, rodada_atual FROM tarefas WHERE id = 100"));
        assertTrue(linha("SELECT encerrada_em FROM tarefas WHERE id = 100").get(0).startsWith("2026-09-01"));
        assertEquals(List.of("6"), linha("SELECT horas FROM estimativas WHERE id = 1002"), "6.4 arredondado");
        assertEquals(List.of("0", "4"), linha("SELECT pontos, horas FROM estimativas WHERE id_tarefa = 102"), "o voto mais recente");

        // dados_extras preserva as colunas; a prioridade antiga não duplica a coluna "Prioridade" do CSV (outra caixa)
        String extras = linha("SELECT dados_extras FROM tarefas WHERE id = 100").get(0);
        assertTrue(extras.contains("\"Prioridade\":\"Alta\"") && !extras.contains("\"prioridade\""), extras);
        assertNull(linha("SELECT dados_extras FROM tarefas WHERE id = 103").get(0));
        assertEquals(List.of("{\"status\":\"Em análise\"}"), linha("SELECT dados_extras FROM tarefas WHERE id = 104"));
    }

    @Test
    void tarefasSemSalaVaoParaASalaDoLegadoModeradaPeloAdmin() throws Exception {
        MigracaoDados.migrar(origem, destino, MigracaoDados.Opcoes.padrao());

        assertEquals(List.of(MigracaoDados.NOME_SALA_LEGADO, "1"),
                linha("SELECT s.nome, s.id_moderador FROM tarefas t JOIN salas s ON s.id = t.id_sala WHERE t.id = 104"));
        assertEquals(List.of("Sprint 1", "FALSE"), linha("SELECT nome, ativa FROM salas WHERE id = 10"));
    }

    @Test
    void senhasEUsuariosVemComoEstao() throws Exception {
        MigracaoDados.migrar(origem, destino, MigracaoDados.Opcoes.padrao());

        assertTrue(linha("SELECT senha_hash FROM usuarios WHERE usuario = 'ANA'").get(0).startsWith("$2a$10$"));
        assertEquals(List.of("JOGADOR", "TRUE"), linha("SELECT perfil, ativo FROM usuarios WHERE usuario = 'JOÃO SILVA'"));
    }

    @Test
    void sequenciasContinuamDepoisDoMaiorId() throws Exception {
        MigracaoDados.migrar(origem, destino, MigracaoDados.Opcoes.padrao());

        try (Statement sql = destino.createStatement()) {
            sql.executeUpdate("INSERT INTO usuarios (usuario, senha_hash, perfil, criado_em) VALUES ('NOVO', 'x', 'JOGADOR', CURRENT_TIMESTAMP)");
        }
        assertEquals(List.of("7"), linha("SELECT id FROM usuarios WHERE usuario = 'NOVO'"));
    }

    @Test
    void simularNaoGravaNada() throws Exception {
        Relatorio relatorio = MigracaoDados.migrar(origem, destino,
                new MigracaoDados.Opcoes(null, MigracaoDados.Opcoes.padrao().zona(), true));

        assertEquals(5, relatorio.migrados("usuarios"));
        assertEquals(List.of("0"), linha("SELECT COUNT(*) FROM usuarios"));
    }

    @Test
    void naoRodaNumDestinoQueJaTemDados() throws Exception {
        MigracaoDados.migrar(origem, destino, MigracaoDados.Opcoes.padrao());

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> MigracaoDados.migrar(origem, destino, MigracaoDados.Opcoes.padrao()));
        assertTrue(erro.getMessage().contains("já tem dados"));
    }

    @Test
    void semAdminParaOLegadoAvisaComoResolver() throws Exception {
        try (Statement sql = origem.createStatement()) {
            sql.executeUpdate("UPDATE usuarios SET tipo_perfil = 'JOGADOR' WHERE id = 1");
        }

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> MigracaoDados.migrar(origem, destino, MigracaoDados.Opcoes.padrao()));
        assertTrue(erro.getMessage().contains("--moderador-legado"));
        assertEquals(List.of("0"), linha("SELECT COUNT(*) FROM usuarios"), "rollback: nada fica pela metade");
    }

    private List<String> linha(String consulta) throws SQLException {
        try (Statement sql = destino.createStatement(); ResultSet rs = sql.executeQuery(consulta)) {
            List<String> valores = new ArrayList<>();
            if (rs.next()) {
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    Object valor = rs.getObject(i);
                    valores.add(valor == null ? null : valor.toString().toUpperCase().equals("TRUE")
                            || valor.toString().equalsIgnoreCase("FALSE") ? valor.toString().toUpperCase() : valor.toString());
                }
            }
            return valores;
        }
    }

    private static void executarScript(Connection conexao, String recurso) throws IOException, SQLException {
        try (InputStream in = MigracaoDadosTest.class.getResourceAsStream(recurso); Statement sql = conexao.createStatement()) {
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)--.*$", "");
            for (String comando : script.split(";")) {
                if (!comando.isBlank()) {
                    sql.execute(comando);
                }
            }
        }
    }
}
