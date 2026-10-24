package com.planningapp.persistence.migracao;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.sql.Connection;

/**
 * Aplica o changelog do Liquibase numa conexão. Usado no deploy (EJB {@code MigracaoBanco}, no planning-service)
 * e no {@code MigracoesTest}, para o teste exercitar exatamente o mesmo caminho.
 *
 * <p>O Liquibase registra o que já rodou na tabela {@code DATABASECHANGELOG} e usa a {@code DATABASECHANGELOGLOCK}
 * para que dois servidores subindo juntos não apliquem a mesma migration duas vezes.</p>
 */
public final class Migracoes {

    public static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    private Migracoes() {
    }

    /**
     * Aplica os changesets pendentes. Não fecha a conexão (é de quem chamou).
     *
     * @param classLoader quem enxerga o changelog; no EAR, o do módulo que chama (o changelog está no lib/)
     */
    public static void aplicar(Connection conexao, ClassLoader classLoader) throws Exception {
        Database banco = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conexao));
        Scope.child(Scope.Attr.resourceAccessor.name(), new ClassLoaderResourceAccessor(classLoader), () ->
                new CommandScope(UpdateCommandStep.COMMAND_NAME)
                        .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, banco)
                        .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, CHANGELOG)
                        .execute());
    }
}
