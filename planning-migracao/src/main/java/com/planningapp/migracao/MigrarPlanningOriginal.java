package com.planningapp.migracao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.ZoneId;

/**
 * Linha de comando da migração (ver MIGRACAO.md):
 *
 * <pre>
 *   ORIGEM_URL=jdbc:postgresql://localhost:5433/planningdb ORIGEM_USUARIO=postgres ORIGEM_SENHA=... \
 *   DESTINO_URL=jdbc:postgresql://localhost:5434/planningjakarta DESTINO_USUARIO=postgres DESTINO_SENHA=... \
 *   java -jar planning-migracao/target/planning-migracao.jar [--simular] [--moderador-legado=USUARIO] [--zona=America/Sao_Paulo]
 * </pre>
 */
public final class MigrarPlanningOriginal {

    private MigrarPlanningOriginal() {
    }

    public static void main(String[] args) {
        MigracaoDados.Opcoes padrao = MigracaoDados.Opcoes.padrao();
        String moderador = padrao.moderadorLegado();
        ZoneId zona = padrao.zona();
        boolean simular = false;
        for (String arg : args) {
            if (arg.equals("--simular")) {
                simular = true;
            } else if (arg.startsWith("--moderador-legado=")) {
                moderador = arg.substring("--moderador-legado=".length());
            } else if (arg.startsWith("--zona=")) {
                zona = ZoneId.of(arg.substring("--zona=".length()));
            } else {
                System.err.println("Opção desconhecida: " + arg);
                System.exit(2);
            }
        }

        String origemUrl = env("ORIGEM_URL", "jdbc:postgresql://localhost:5433/planningdb");
        String destinoUrl = env("DESTINO_URL", "jdbc:postgresql://localhost:5434/planningjakarta");
        System.out.println("Origem:  " + origemUrl);
        System.out.println("Destino: " + destinoUrl + (simular ? "  (simulação: nada será gravado)" : ""));

        try (Connection origem = DriverManager.getConnection(origemUrl, env("ORIGEM_USUARIO", "postgres"), env("ORIGEM_SENHA", "postgres"));
             Connection destino = DriverManager.getConnection(destinoUrl, env("DESTINO_USUARIO", "postgres"), env("DESTINO_SENHA", "postgres"))) {
            origem.setReadOnly(true);
            Relatorio relatorio = MigracaoDados.migrar(origem, destino, new MigracaoDados.Opcoes(moderador, zona, simular));
            System.out.println();
            System.out.println(relatorio);
            System.out.println(simular ? "Simulação concluída." : "Migração concluída.");
        } catch (Exception e) {
            System.err.println();
            System.err.println("A migração falhou e nada foi gravado no destino: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static String env(String nome, String padrao) {
        // Só a variável ausente usa o padrão: senha vazia é uma senha válida
        String valor = System.getenv(nome);
        return valor == null ? padrao : valor;
    }
}
