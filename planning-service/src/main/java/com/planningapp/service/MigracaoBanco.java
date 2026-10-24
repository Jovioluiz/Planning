package com.planningapp.service;

import com.planningapp.persistence.migracao.Migracoes;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.logging.Logger;

/**
 * Aplica as migrations do Liquibase no deploy, antes de qualquer outro código usar o banco. Quem também roda
 * no startup e usa o banco declara {@code @DependsOn("MigracaoBanco")} (ex.: {@link CriacaoSuperUsuario}).
 * Requisições REST e WebSocket só chegam depois que o deploy termina.
 *
 * <p>{@code BEAN} (sem transação do container): o Liquibase faz os próprios commits na conexão, e numa
 * transação JTA aberta pelo container isso é proibido ("You cannot commit during a managed transaction").</p>
 *
 * <p>Se uma migration falhar, a exceção sobe e o deploy falha: melhor não subir do que subir com o banco pela
 * metade.</p>
 */
@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class MigracaoBanco {

    private static final Logger LOG = Logger.getLogger(MigracaoBanco.class.getName());

    /** O mesmo datasource do persistence.xml. */
    @Resource(lookup = "java:jboss/datasources/PlanningDS")
    private DataSource dataSource;

    @PostConstruct
    void migrar() {
        long inicio = System.currentTimeMillis();
        try (Connection conexao = dataSource.getConnection()) {
            Migracoes.aplicar(conexao, MigracaoBanco.class.getClassLoader());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao aplicar as migrations do banco: " + e.getMessage(), e);
        }
        LOG.info("Migrations do banco aplicadas em " + (System.currentTimeMillis() - inicio) + " ms");
    }
}
