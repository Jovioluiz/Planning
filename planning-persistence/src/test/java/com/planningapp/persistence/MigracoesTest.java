package com.planningapp.persistence;

import com.planningapp.persistence.migracao.Migracoes;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.persistence.ValidationMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garante, no build, que as migrations e as entidades concordam: aplica o changelog num H2 em memória e sobe o
 * Hibernate com {@code hbm2ddl=validate}. Se alguém mudar uma entidade e esquecer a migration (ou o contrário),
 * quebra aqui, e não no deploy.
 */
class MigracoesTest {

    private static final ClassLoader CLASSLOADER = MigracoesTest.class.getClassLoader();

    /**
     * Colunas criadas por changesets posteriores ao 003 (quando o Liquibase foi adotado). Ao criar uma coluna nova
     * num changeset, inclua-a aqui: o teste de adoção simula um banco da época do hbm2ddl, que não a tinha.
     */
    private static final List<String> COLUNAS_POSTERIORES_AO_LIQUIBASE = List.of(
            "tarefas.dados_extras", "tarefas.horas_liberadas_em");

    private static final List<String> TABELAS_POSTERIORES_AO_LIQUIBASE = List.of("configuracoes_redmine");

    @Test
    void changelogCriaOSchemaQueAsEntidadesEsperam() throws Exception {
        String url = novoBanco();
        try (Connection conexao = DriverManager.getConnection(url, "sa", "")) {
            Migracoes.aplicar(conexao, CLASSLOADER);
        }

        assertDoesNotThrow(() -> validarComHibernate(url));
    }

    @Test
    void aplicarDeNovoNaoFazNada() throws Exception {
        String url = novoBanco();
        try (Connection conexao = DriverManager.getConnection(url, "sa", "")) {
            Migracoes.aplicar(conexao, CLASSLOADER);
            int antes = changesetsAplicados(conexao);
            Migracoes.aplicar(conexao, CLASSLOADER);

            assertEquals(antes, changesetsAplicados(conexao));
            assertTrue(antes >= 2, "001 e 003 rodam no H2; o 002 é só para Postgres");
        }
    }

    @Test
    void checksDosEnumsRecusamValoresDesconhecidos() throws Exception {
        String url = novoBanco();
        try (Connection conexao = DriverManager.getConnection(url, "sa", "");
             Statement sql = conexao.createStatement()) {
            Migracoes.aplicar(conexao, CLASSLOADER);

            assertThrows(SQLException.class, () -> sql.executeUpdate("""
                    INSERT INTO usuarios (usuario, senha_hash, perfil, criado_em) VALUES ('x', 'h', 'GERENTE', CURRENT_TIMESTAMP)"""));
            assertEquals(1, sql.executeUpdate("""
                    INSERT INTO usuarios (usuario, senha_hash, perfil, criado_em) VALUES ('ana', 'h', 'JOGADOR', CURRENT_TIMESTAMP)"""),
                    "o default de ativo também precisa funcionar");
        }
    }

    /** O caminho de quem já tinha um banco criado pelo hbm2ddl: o 001 é só marcado como aplicado. */
    @Test
    void bancoCriadoPeloHbm2ddlAdotaAsMigrations() throws Exception {
        String url = novoBanco();
        try (EntityManagerFactory emf = configuracao(url, "create").createEntityManagerFactory()) {
            assertTrue(emf.isOpen());
        }
        try (Connection conexao = DriverManager.getConnection(url, "sa", "")) {
            // O hbm2ddl acima usa as entidades de hoje; um banco daquela época não tinha o que veio depois
            try (Statement sql = conexao.createStatement()) {
                for (String coluna : COLUNAS_POSTERIORES_AO_LIQUIBASE) {
                    sql.execute("ALTER TABLE " + coluna.replace(".", " DROP COLUMN "));
                }
                for (String tabela : TABELAS_POSTERIORES_AO_LIQUIBASE) {
                    sql.execute("DROP TABLE " + tabela);
                }
            }
            Migracoes.aplicar(conexao, CLASSLOADER);
            try (Statement sql = conexao.createStatement();
                 var rs = sql.executeQuery("SELECT EXECTYPE FROM DATABASECHANGELOG WHERE ID = '001-esquema-inicial'")) {
                assertTrue(rs.next());
                assertEquals("MARK_RAN", rs.getString(1));
            }
        }

        assertDoesNotThrow(() -> validarComHibernate(url));
    }

    private static void validarComHibernate(String url) throws IOException {
        try (EntityManagerFactory emf = configuracao(url, "validate").createEntityManagerFactory()) {
            assertTrue(emf.isOpen());
        }
    }

    /** As mesmas entidades do persistence.xml de produção (que é JTA e não serve fora do servidor). */
    private static PersistenceConfiguration configuracao(String url, String hbm2ddl) throws IOException {
        PersistenceConfiguration configuracao = new PersistenceConfiguration("validacao")
                .provider("org.hibernate.jpa.HibernatePersistenceProvider")
                .transactionType(PersistenceUnitTransactionType.RESOURCE_LOCAL)
                .validationMode(ValidationMode.NONE)
                .property(PersistenceConfiguration.JDBC_URL, url)
                .property(PersistenceConfiguration.JDBC_USER, "sa")
                .property(PersistenceConfiguration.JDBC_PASSWORD, "")
                .property("hibernate.hbm2ddl.auto", hbm2ddl);
        for (String classe : entidadesDoPersistenceXml()) {
            configuracao.managedClass(carregar(classe));
        }
        return configuracao;
    }

    private static List<String> entidadesDoPersistenceXml() throws IOException {
        try (InputStream xml = CLASSLOADER.getResourceAsStream("META-INF/persistence.xml")) {
            String conteudo = new String(xml.readAllBytes(), StandardCharsets.UTF_8);
            List<String> classes = Pattern.compile("<class>\\s*([\\w.]+)\\s*</class>").matcher(conteudo)
                    .results().map(m -> m.group(1)).toList();
            assertFalse(classes.isEmpty());
            return classes;
        }
    }

    private static int changesetsAplicados(Connection conexao) throws SQLException {
        try (Statement sql = conexao.createStatement(); var rs = sql.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String novoBanco() {
        return "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    private static Class<?> carregar(String nome) {
        try {
            return Class.forName(nome);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
