package com.planningapp.service.importacao;

import com.planningapp.dto.CriarTarefaRequest;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Converte o arquivo (CSV) ou o corpo (JSON) da importação em linhas e valida. Sem dependência de
 * container, para ter testes unitários. As regras de banco (duplicadas, sala ativa) ficam no service.
 */
public final class ImportacaoTarefas {

    public static final int MAXIMO = 500;

    /**
     * @param linha       linha do CSV ou posição na lista JSON (a partir de 1), para as mensagens de erro
     * @param dadosExtras colunas do arquivo além do número (o front as mostra como colunas dinâmicas)
     */
    public record Linha(int linha, String numero, String titulo, String descricao, String sprint,
                        Map<String, String> dadosExtras) {
    }

    private ImportacaoTarefas() {
    }

    /**
     * Precisa de cabeçalho com as colunas {@code numero} e {@code titulo}; {@code descricao} e {@code sprint}
     * (as do CSV do original) são opcionais. Os nomes não diferenciam maiúsculas nem acentos ({@code Número}
     * vale). Como no front do original, todas as colunas exceto {@code numero} também vão para {@code dadosExtras},
     * com o nome do cabeçalho como veio, para aparecerem na tabela de tarefas.
     */
    public static List<Linha> deCsv(String conteudo) {
        List<LeitorCsv.Registro> registros = LeitorCsv.ler(conteudo);
        if (registros.isEmpty()) {
            throw new IllegalArgumentException("O arquivo está vazio");
        }
        List<String> cabecalho = registros.get(0).campos().stream().map(ImportacaoTarefas::normalizar).toList();
        int numero = cabecalho.indexOf("numero");
        int titulo = cabecalho.indexOf("titulo");
        int descricao = cabecalho.indexOf("descricao");
        int sprint = cabecalho.indexOf("sprint");
        if (numero < 0 || titulo < 0) {
            throw new IllegalArgumentException("O CSV precisa das colunas numero e titulo no cabeçalho (encontradas: "
                    + String.join(", ", registros.get(0).campos()) + ")");
        }
        return registros.stream().skip(1)
                .map(r -> new Linha(r.linha(), campo(r, numero), campo(r, titulo),
                        descricao < 0 ? null : campo(r, descricao), sprint < 0 ? null : campo(r, sprint),
                        extras(registros.get(0).campos(), r, numero)))
                .toList();
    }

    public static List<Linha> deJson(List<CriarTarefaRequest> itens) {
        return IntStream.range(0, itens.size())
                .mapToObj(i -> {
                    CriarTarefaRequest item = itens.get(i);
                    return item == null
                            ? new Linha(i + 1, null, null, null, null, null)
                            : new Linha(i + 1, item.numero(), item.titulo(), item.descricao(), item.sprint(), item.dadosExtras());
                })
                .toList();
    }

    /** Todos os problemas de uma vez, para o usuário corrigir o arquivo numa passada só. */
    public static List<String> validar(List<Linha> linhas) {
        List<String> erros = new ArrayList<>();
        if (linhas.isEmpty()) {
            erros.add("Nenhuma tarefa para importar");
        }
        if (linhas.size() > MAXIMO) {
            erros.add("No máximo " + MAXIMO + " tarefas por importação (recebidas: " + linhas.size() + ")");
            return erros;
        }
        for (Linha l : linhas) {
            if (vazio(l.numero())) {
                erros.add("Linha " + l.linha() + ": o número é obrigatório");
            } else if (l.numero().trim().length() > 50) {
                erros.add("Linha " + l.linha() + ": o número deve ter no máximo 50 caracteres");
            }
            if (vazio(l.titulo())) {
                erros.add("Linha " + l.linha() + ": o título é obrigatório");
            } else if (l.titulo().trim().length() > 255) {
                erros.add("Linha " + l.linha() + ": o título deve ter no máximo 255 caracteres");
            }
            if (l.sprint() != null && l.sprint().trim().length() > 50) {
                erros.add("Linha " + l.linha() + ": a sprint deve ter no máximo 50 caracteres");
            }
        }
        return erros;
    }

    private static Map<String, String> extras(List<String> cabecalho, LeitorCsv.Registro registro, int colunaNumero) {
        Map<String, String> extras = new LinkedHashMap<>();
        for (int i = 0; i < cabecalho.size(); i++) {
            String valor = campo(registro, i);
            if (i != colunaNumero && valor != null && !valor.isBlank() && !cabecalho.get(i).isBlank()) {
                extras.put(cabecalho.get(i).trim(), valor.trim());
            }
        }
        return extras.isEmpty() ? null : extras;
    }

    private static String campo(LeitorCsv.Registro registro, int indice) {
        return indice < registro.campos().size() ? registro.campos().get(indice) : null;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String normalizar(String coluna) {
        return Normalizer.normalize(coluna.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
