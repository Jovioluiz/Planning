package com.planningapp.service.importacao;

import com.planningapp.dto.CriarTarefaRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportacaoTarefasTest {

    @Test
    void leCsvDoExcelComPontoEVirgulaBomEAcentosNoCabecalho() {
        String csv = "﻿Número;Título;Descrição;sprint\r\nPP-1;Login;Tela de login;S1\r\n\r\nPP-2;Cadastro;;S1\r\n";

        List<ImportacaoTarefas.Linha> linhas = ImportacaoTarefas.deCsv(csv);

        assertEquals(List.of(
                new ImportacaoTarefas.Linha(2, "PP-1", "Login", "Tela de login", "S1",
                        Map.of("Título", "Login", "Descrição", "Tela de login", "sprint", "S1")),
                new ImportacaoTarefas.Linha(4, "PP-2", "Cadastro", "", "S1",
                        Map.of("Título", "Cadastro", "sprint", "S1"))), linhas);
    }

    @Test
    void camposEntreAspasPodemTerSeparadorQuebraDeLinhaEAspas() {
        String csv = "numero,titulo,descricao\nPP-1,\"Login, com SSO\",\"Linha 1\nLinha 2 com \"\"aspas\"\"\"\nPP-2,Cadastro,x\n";

        List<ImportacaoTarefas.Linha> linhas = ImportacaoTarefas.deCsv(csv);

        assertEquals("Login, com SSO", linhas.get(0).titulo());
        assertEquals("Linha 1\nLinha 2 com \"aspas\"", linhas.get(0).descricao());
        assertEquals(4, linhas.get(1).linha(), "a linha conta as quebras dentro das aspas");
    }

    @Test
    void exigeAsColunasNumeroETitulo() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> ImportacaoTarefas.deCsv("codigo;nome\nPP-1;Login"));

        assertTrue(erro.getMessage().contains("numero e titulo"));
    }

    @Test
    void aspasSemFechamentoSaoErro() {
        assertThrows(IllegalArgumentException.class, () -> ImportacaoTarefas.deCsv("numero;titulo\nPP-1;\"Login"));
    }

    @Test
    void validaTodasAsLinhasDeUmaVez() {
        List<ImportacaoTarefas.Linha> linhas = ImportacaoTarefas.deCsv("numero;titulo\nPP-1;Login\n;Sem número\nPP-3;\n");

        assertEquals(List.of("Linha 3: o número é obrigatório", "Linha 4: o título é obrigatório"),
                ImportacaoTarefas.validar(linhas));
    }

    @Test
    void jsonUsaAPosicaoNaListaComoLinha() {
        List<ImportacaoTarefas.Linha> linhas = ImportacaoTarefas.deJson(Arrays.asList(
                new CriarTarefaRequest("PP-1", "Login", null, null, null), null));

        assertEquals(List.of("Linha 2: o número é obrigatório", "Linha 2: o título é obrigatório"),
                ImportacaoTarefas.validar(linhas));
    }

    @Test
    void limitaOTamanhoDaImportacao() {
        List<ImportacaoTarefas.Linha> linhas = java.util.stream.IntStream.rangeClosed(1, ImportacaoTarefas.MAXIMO + 1)
                .mapToObj(i -> new ImportacaoTarefas.Linha(i, "PP-" + i, "T", null, null, null))
                .toList();

        assertTrue(ImportacaoTarefas.validar(linhas).get(0).startsWith("No máximo 500"));
    }
}
