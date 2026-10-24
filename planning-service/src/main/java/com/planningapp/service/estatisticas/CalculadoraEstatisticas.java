package com.planningapp.service.estatisticas;

import com.planningapp.dto.EstatisticasDTO;
import com.planningapp.dto.EstatisticasDTO.Opcao;
import com.planningapp.dto.EstatisticasDTO.PorSprint;
import com.planningapp.dto.EstatisticasDTO.PorUsuario;
import com.planningapp.dto.EstatisticasDTO.Resumo;
import com.planningapp.dto.EstatisticasDTO.TarefaEstatistica;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Regras das estatísticas, portadas do {@code EstatisticasService} do Planning original. Recebe dados já
 * carregados (records simples, sem JPA), então roda e é testada sem container.
 */
public final class CalculadoraEstatisticas {

    /** Agrupamento das tarefas sem sprint, como no original. */
    public static final String SEM_SPRINT = "Sem sprint";

    /** Voto da rodada final de uma tarefa. Para JOGADOR, pontos/horas; para TESTE, horasTeste. */
    public record Voto(String usuario, String perfil, Integer pontos, Integer horas, Integer horasTeste) {
    }

    /** @param votos só os da rodada final (as anteriores não contam, como no original) */
    public record TarefaEncerrada(Long id, String numero, String titulo, String sprint, Long salaId, String sala,
                                  boolean pulada, int rodadas, Integer pontosFinais, Integer horasFinais,
                                  Integer horasTesteFinais, Instant liberadaEm, Instant encerradaEm, List<Voto> votos) {
    }

    /** Filtros opcionais (nulos = sem filtro). {@code fim} é exclusivo. */
    public record Filtro(Long salaId, String sprint, Instant inicio, Instant fim) {

        public static Filtro nenhum() {
            return new Filtro(null, null, null, null);
        }
    }

    private CalculadoraEstatisticas() {
    }

    public static EstatisticasDTO calcular(List<TarefaEncerrada> noEscopo, Filtro filtro) {
        List<Opcao> salas = noEscopo.stream()
                .collect(Collectors.toMap(TarefaEncerrada::salaId, t -> t.sala(), (a, b) -> a, TreeMap::new))
                .entrySet().stream().map(e -> new Opcao(e.getKey(), e.getValue())).toList();

        List<TarefaEncerrada> daSala = noEscopo.stream()
                .filter(t -> filtro.salaId() == null || filtro.salaId().equals(t.salaId()))
                .toList();
        List<String> sprints = daSala.stream().map(CalculadoraEstatisticas::sprintDe).distinct().sorted().toList();

        List<TarefaEncerrada> filtradas = daSala.stream()
                .filter(t -> filtro.sprint() == null || filtro.sprint().isBlank() || filtro.sprint().equals(sprintDe(t)))
                .filter(t -> filtro.inicio() == null || (t.encerradaEm() != null && !t.encerradaEm().isBefore(filtro.inicio())))
                .filter(t -> filtro.fim() == null || (t.encerradaEm() != null && t.encerradaEm().isBefore(filtro.fim())))
                .toList();

        List<TarefaEncerrada> estimadas = filtradas.stream().filter(t -> !t.pulada()).toList();
        List<TarefaEncerrada> puladas = filtradas.stream().filter(TarefaEncerrada::pulada).toList();

        Map<Long, TarefaEstatistica> porTarefa = new LinkedHashMap<>();
        estimadas.stream()
                .sorted(Comparator.comparing(TarefaEncerrada::encerradaEm, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(t -> porTarefa.put(t.id(), calcularTarefa(t)));

        List<Voto> votos = estimadas.stream().flatMap(t -> t.votos().stream()).toList();
        return new EstatisticasDTO(
                resumo(porTarefa.values(), puladas.size(), votos),
                porSprint(estimadas, puladas, porTarefa),
                porUsuario(estimadas, porTarefa),
                distribuicao(votos.stream().map(Voto::pontos).filter(Objects::nonNull).toList()),
                distribuicao(votos.stream().map(Voto::horas).filter(CalculadoraEstatisticas::positivo).toList()),
                new ArrayList<>(porTarefa.values()),
                salas,
                sprints);
    }

    static String sprintDe(TarefaEncerrada t) {
        return t.sprint() == null || t.sprint().isBlank() ? SEM_SPRINT : t.sprint().trim();
    }

    private static TarefaEstatistica calcularTarefa(TarefaEncerrada t) {
        List<Integer> pontos = t.votos().stream().map(Voto::pontos).filter(CalculadoraEstatisticas::positivo).toList();
        List<Integer> horas = t.votos().stream().map(Voto::horas).filter(CalculadoraEstatisticas::positivo).toList();
        List<Integer> horasTeste = t.votos().stream().map(Voto::horasTeste).filter(CalculadoraEstatisticas::positivo).toList();

        Double duracao = null;
        if (t.liberadaEm() != null && t.encerradaEm() != null && t.encerradaEm().isAfter(t.liberadaEm())) {
            duracao = arred(Duration.between(t.liberadaEm(), t.encerradaEm()).toSeconds() / 60.0);
        }

        return new TarefaEstatistica(
                t.id(), t.numero(), t.titulo(), sprintDe(t), t.sala(),
                (int) t.votos().stream().filter(v -> v.pontos() != null).count(),
                moda(pontos),
                media(pontos),
                pontos.stream().min(Integer::compare).orElse(null),
                pontos.stream().max(Integer::compare).orElse(null),
                media(horas),
                media(horasTeste),
                t.pontosFinais(), t.horasFinais(), t.horasTesteFinais(),
                t.rodadas(),
                !pontos.isEmpty() && pontos.stream().distinct().count() == 1,
                t.encerradaEm(),
                duracao);
    }

    private static Resumo resumo(Collection<TarefaEstatistica> tarefas, int puladas, List<Voto> votos) {
        long totalVotos = votos.stream().filter(v -> v.pontos() != null).count();
        long participantes = votos.stream().map(Voto::usuario).distinct().count();
        Agregado a = agregar(tarefas);
        return new Resumo(tarefas.size(), puladas, totalVotos, (int) participantes,
                a.totalPontos(), a.mediaPontos(), a.totalHoras(), a.mediaHoras(), a.totalHorasTeste(), a.mediaHorasTeste(),
                a.mediaRodadas(), a.taxaConsenso(),
                media(tarefas.stream().map(TarefaEstatistica::duracaoMinutos).filter(Objects::nonNull).toList()));
    }

    private static List<PorSprint> porSprint(List<TarefaEncerrada> estimadas, List<TarefaEncerrada> puladas,
                                             Map<Long, TarefaEstatistica> porTarefa) {
        Map<String, List<TarefaEstatistica>> grupos = new TreeMap<>();
        estimadas.forEach(t -> grupos.computeIfAbsent(sprintDe(t), k -> new ArrayList<>()).add(porTarefa.get(t.id())));
        Map<String, Long> puladasPorSprint = puladas.stream()
                .collect(Collectors.groupingBy(CalculadoraEstatisticas::sprintDe, Collectors.counting()));
        puladasPorSprint.keySet().forEach(s -> grupos.computeIfAbsent(s, k -> new ArrayList<>()));

        return grupos.entrySet().stream().map(e -> {
            Agregado a = agregar(e.getValue());
            return new PorSprint(e.getKey(), e.getValue().size(), puladasPorSprint.getOrDefault(e.getKey(), 0L).intValue(),
                    a.totalPontos(), a.mediaPontos(), a.totalHoras(), a.mediaHoras(), a.totalHorasTeste(),
                    a.mediaRodadas(), a.taxaConsenso());
        }).toList();
    }

    private static List<PorUsuario> porUsuario(List<TarefaEncerrada> estimadas, Map<Long, TarefaEstatistica> porTarefa) {
        record VotoNaTarefa(Long tarefaId, Voto voto) {
        }
        Map<String, List<VotoNaTarefa>> porUsuario = estimadas.stream()
                .flatMap(t -> t.votos().stream().map(v -> new VotoNaTarefa(t.id(), v)))
                .collect(Collectors.groupingBy(v -> v.voto().usuario(), TreeMap::new, Collectors.toList()));

        return porUsuario.entrySet().stream().map(e -> {
            List<VotoNaTarefa> votos = e.getValue();
            List<VotoNaTarefa> comPontos = votos.stream().filter(v -> positivo(v.voto().pontos())).toList();
            List<Integer> horas = votos.stream().map(v -> v.voto().horas()).filter(CalculadoraEstatisticas::positivo).toList();
            List<Integer> horasTeste = votos.stream().map(v -> v.voto().horasTeste()).filter(CalculadoraEstatisticas::positivo).toList();

            // Desvio com sinal em relação aos pontos que valeram: positivo = estima acima
            List<Double> desvios = new ArrayList<>();
            long acertos = 0;
            for (VotoNaTarefa v : comPontos) {
                TarefaEstatistica t = porTarefa.get(v.tarefaId());
                Double referencia = pontosReferencia(t);
                if (referencia == null) {
                    continue;
                }
                desvios.add(v.voto().pontos() - referencia);
                Integer cartaFinal = t.pontosFinais() != null ? t.pontosFinais() : t.modaPontos();
                if (v.voto().pontos().equals(cartaFinal)) {
                    acertos++;
                }
            }

            return new PorUsuario(
                    e.getKey(),
                    votos.get(0).voto().perfil(),
                    (int) votos.stream().map(VotoNaTarefa::tarefaId).distinct().count(),
                    comPontos.size(),
                    votos.stream().filter(v -> v.voto().pontos() != null && v.voto().pontos() == 0).count(),
                    media(comPontos.stream().map(v -> v.voto().pontos()).toList()),
                    horas.size(),
                    media(horas),
                    horasTeste.size(),
                    media(horasTeste),
                    media(desvios),
                    comPontos.isEmpty() ? null : arred(100.0 * acertos / comPontos.size()));
        }).toList();
    }

    /** Chaves em texto (JSON só tem chave texto) mas na ordem numérica: "2" antes de "13". */
    private static Map<String, Long> distribuicao(List<Integer> valores) {
        Map<String, Long> resultado = new LinkedHashMap<>();
        valores.stream()
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()))
                .forEach((valor, quantidade) -> resultado.put(String.valueOf(valor), quantidade));
        return resultado;
    }

    private record Agregado(Double totalPontos, Double mediaPontos, Double totalHoras, Double mediaHoras,
                            Double totalHorasTeste, Double mediaHorasTeste, Double mediaRodadas, Double taxaConsenso) {
    }

    /** O valor final do moderador ou, se ele não informou, a média dos votos. */
    private static Double pontosReferencia(TarefaEstatistica t) {
        return t.pontosFinais() != null ? Double.valueOf(t.pontosFinais()) : t.mediaPontos();
    }

    private static Double horasReferencia(TarefaEstatistica t) {
        return t.horasFinais() != null ? Double.valueOf(t.horasFinais()) : t.mediaHoras();
    }

    private static Double horasTesteReferencia(TarefaEstatistica t) {
        return t.horasTesteFinais() != null ? Double.valueOf(t.horasTesteFinais()) : t.mediaHorasTeste();
    }

    private static Agregado agregar(Collection<TarefaEstatistica> tarefas) {
        List<Double> pontos = tarefas.stream().map(CalculadoraEstatisticas::pontosReferencia).filter(Objects::nonNull).toList();
        List<Double> horas = tarefas.stream().map(CalculadoraEstatisticas::horasReferencia).filter(Objects::nonNull).toList();
        List<Double> horasTeste = tarefas.stream().map(CalculadoraEstatisticas::horasTesteReferencia).filter(Objects::nonNull).toList();
        long comVotos = tarefas.stream().filter(t -> t.mediaPontos() != null).count();
        long consenso = tarefas.stream().filter(TarefaEstatistica::consenso).count();
        return new Agregado(
                total(pontos), media(pontos),
                total(horas), media(horas),
                total(horasTeste), media(horasTeste),
                media(tarefas.stream().map(TarefaEstatistica::rodadas).toList()),
                comVotos == 0 ? null : arred(100.0 * consenso / comVotos));
    }

    /** Valor mais votado; em empate, o maior (estimativa mais conservadora), como no original. */
    private static Integer moda(List<Integer> valores) {
        return valores.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static boolean positivo(Integer valor) {
        return valor != null && valor > 0;
    }

    private static Double total(List<? extends Number> valores) {
        return valores.isEmpty() ? null : arred(soma(valores));
    }

    private static Double media(List<? extends Number> valores) {
        return valores.isEmpty() ? null : arred(soma(valores) / valores.size());
    }

    private static double soma(List<? extends Number> valores) {
        return valores.stream().mapToDouble(Number::doubleValue).sum();
    }

    private static Double arred(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
