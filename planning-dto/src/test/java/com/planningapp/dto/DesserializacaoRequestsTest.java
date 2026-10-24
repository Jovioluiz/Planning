package com.planningapp.dto;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Os records de entrada (*Request) precisam ser desserializáveis pelo Yasson, o JSON-B do WildFly. Um
 * construtor a mais no record quebra todas as requisições daquele tipo, e só em tempo de execução
 * ("Cannot create instance of a record ... Multiple constructors found"). Isso já aconteceu com o
 * {@code CriarTarefaRequest}.
 */
class DesserializacaoRequestsTest {

    private final Jsonb jsonb = JsonbBuilder.create();

    @Test
    void todosOsRequestsSaoDesserializaveis() throws Exception {
        List<Class<?>> requests = requestsDoPacote();
        assertFalse(requests.isEmpty());

        for (Class<?> tipo : requests) {
            assertDoesNotThrow(() -> jsonb.fromJson("{}", tipo), tipo.getSimpleName());
        }
    }

    @Test
    void criarTarefaRequestLeOsCampos() {
        CriarTarefaRequest request = jsonb.fromJson(
                "{\"numero\":\"PP-1\",\"titulo\":\"Login\",\"sprint\":\"S1\",\"desconhecido\":1,"
                        + "\"dadosExtras\":{\"Prioridade\":\"Alta\",\"Responsável\":\"Ana\"}}", CriarTarefaRequest.class);

        assertEquals(new CriarTarefaRequest("PP-1", "Login", null, "S1",
                java.util.Map.of("Prioridade", "Alta", "Responsável", "Ana")), request);
    }

    private static List<Class<?>> requestsDoPacote() throws IOException, URISyntaxException {
        // Onde estão as classes de produção (target/classes); getResource("") cairia no test-classes, mesmo pacote
        Path classes = Path.of(CriarTarefaRequest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path pacote = classes.resolve(CriarTarefaRequest.class.getPackageName().replace('.', '/'));
        try (Stream<Path> arquivos = Files.list(pacote)) {
            return arquivos.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith("Request.class"))
                    .<Class<?>>map(n -> carregar("com.planningapp.dto." + n.replace(".class", "")))
                    .filter(Class::isRecord)
                    .toList();
        }
    }

    private static Class<?> carregar(String nome) {
        try {
            return Class.forName(nome);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
