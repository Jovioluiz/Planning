package com.planningapp.service;

import com.planningapp.dto.EstatisticasDTO;
import com.planningapp.dto.EstatisticasDTO.*;
import com.planningapp.entity.Estimation;
import com.planningapp.entity.Sala;
import com.planningapp.entity.Task;
import com.planningapp.repository.EstimationRepository;
import com.planningapp.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EstatisticasService {

    /** Sprint usada no agrupamento quando a tarefa não tem sprint definida. */
    public static final String SEM_SPRINT = "Sem sprint";

    /** Valor do filtro de sala que seleciona tarefas legadas (sem sala). */
    public static final long SALA_LEGADO = 0L;

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Autowired private TaskRepository taskRepository;
    @Autowired private EstimationRepository estimationRepository;

    /**
     * @param moderador usuário ADMIN cujo escopo é aplicado (salas que modera + tarefas legadas);
     *                  {@code null} para ver tudo (SUPER)
     */
    @Transactional(readOnly = true)
    public EstatisticasDTO calcular(String moderador, Long salaId, String sprint, LocalDate de, LocalDate ate) {
        List<Task> noEscopo = taskRepository.findByEstimadaTrue().stream()
                .filter(t -> moderador == null || t.getSala() == null
                        || moderador.equals(t.getSala().getModerador().getUsuario()))
                .toList();

        List<Opcao> salas = noEscopo.stream()
                .map(Task::getSala).filter(Objects::nonNull)
                .collect(Collectors.toMap(Sala::getId, s -> s, (a, b) -> a, TreeMap::new))
                .values().stream().map(s -> new Opcao(s.getId(), s.getNome())).toList();
        if (noEscopo.stream().anyMatch(t -> t.getSala() == null)) {
            salas = new ArrayList<>(salas);
            salas.add(0, new Opcao(SALA_LEGADO, "Sem sala (legado)"));
        }

        List<Task> daSala = noEscopo.stream().filter(t -> filtraSala(t, salaId)).toList();
        List<String> sprints = daSala.stream().map(this::sprintDe).distinct().sorted().toList();

        Instant inicio = de != null ? de.atStartOfDay(ZONA).toInstant() : null;
        Instant fim = ate != null ? ate.plusDays(1).atStartOfDay(ZONA).toInstant() : null;
        List<Task> filtradas = daSala.stream()
                .filter(t -> sprint == null || sprint.isBlank() || sprint.equals(sprintDe(t)))
                .filter(t -> inicio == null || (t.getEstimadaEm() != null && !t.getEstimadaEm().isBefore(inicio)))
                .filter(t -> fim == null || (t.getEstimadaEm() != null && t.getEstimadaEm().isBefore(fim)))
                .toList();

        List<Task> estimadas = filtradas.stream().filter(t -> !t.isPulada()).toList();
        List<Task> puladas = filtradas.stream().filter(Task::isPulada).toList();

        // Votos da rodada final de cada tarefa
        Map<Long, List<Estimation>> votosPorTarefa = new HashMap<>();
        if (!estimadas.isEmpty()) {
            Map<Long, Integer> rodadaFinal = estimadas.stream()
                    .collect(Collectors.toMap(Task::getId, Task::getRodadaAtual));
            for (Estimation e : estimationRepository.findByTaskIdIn(new ArrayList<>(rodadaFinal.keySet()))) {
                if (Objects.equals(e.getRodada(), rodadaFinal.get(e.getTaskId()))) {
                    votosPorTarefa.computeIfAbsent(e.getTaskId(), k -> new ArrayList<>()).add(e);
                }
            }
        }

        Map<Long, TarefaEstatistica> statsPorTarefa = new LinkedHashMap<>();
        estimadas.stream()
                .sorted(Comparator.comparing(Task::getEstimadaEm, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(t -> statsPorTarefa.put(t.getId(),
                        calcularTarefa(t, votosPorTarefa.getOrDefault(t.getId(), List.of()))));

        return new EstatisticasDTO(
                resumo(statsPorTarefa.values(), puladas.size(), votosPorTarefa),
                porSprint(estimadas, puladas, statsPorTarefa),
                porUsuario(votosPorTarefa, statsPorTarefa),
                distribuicaoPontos(votosPorTarefa),
                distribuicaoHoras(votosPorTarefa),
                new ArrayList<>(statsPorTarefa.values()),
                salas,
                sprints
        );
    }

    private boolean filtraSala(Task t, Long salaId) {
        if (salaId == null) return true;
        if (salaId == SALA_LEGADO) return t.getSala() == null;
        return t.getSala() != null && salaId.equals(t.getSala().getId());
    }

    private String sprintDe(Task t) {
        return t.getSprint() == null || t.getSprint().isBlank() ? SEM_SPRINT : t.getSprint().trim();
    }

    private TarefaEstatistica calcularTarefa(Task t, List<Estimation> votos) {
        List<Integer> pontos = votos.stream().map(Estimation::getPontos)
                .filter(p -> p != null && p > 0).toList();
        List<Double> horas = votos.stream().map(Estimation::getHoras)
                .filter(h -> h != null && h > 0).toList();
        List<Double> horasTeste = votos.stream().map(Estimation::getHorasTeste)
                .filter(h -> h != null && h > 0).toList();

        Double duracao = null;
        if (t.getLiberadaEm() != null && t.getEstimadaEm() != null && t.getEstimadaEm().isAfter(t.getLiberadaEm())) {
            duracao = arred(Duration.between(t.getLiberadaEm(), t.getEstimadaEm()).toSeconds() / 60.0);
        }

        return new TarefaEstatistica(
                t.getId(),
                t.getNumero(),
                t.getTitulo(),
                sprintDe(t),
                t.getSala() != null ? t.getSala().getNome() : null,
                (int) votos.stream().filter(v -> v.getPontos() != null).count(),
                moda(pontos),
                media(pontos),
                pontos.stream().min(Integer::compare).orElse(null),
                pontos.stream().max(Integer::compare).orElse(null),
                media(horas),
                media(horasTeste),
                t.getPontosFinais(),
                t.getHorasFinais(),
                t.getHorasTesteFinais(),
                t.getRodadaAtual(),
                !pontos.isEmpty() && pontos.stream().distinct().count() == 1,
                t.getEstimadaEm(),
                duracao
        );
    }

    private Resumo resumo(Collection<TarefaEstatistica> tarefas, int puladas, Map<Long, List<Estimation>> votos) {
        long totalVotos = votos.values().stream().flatMap(List::stream)
                .filter(v -> v.getPontos() != null).count();
        long participantes = votos.values().stream().flatMap(List::stream)
                .map(v -> v.getUsuario().getUsuario()).distinct().count();
        Agregado a = agregar(tarefas);
        return new Resumo(tarefas.size(), puladas, totalVotos, (int) participantes,
                a.totalPontos, a.mediaPontos, a.totalHoras, a.mediaHoras, a.mediaRodadas, a.taxaConsenso,
                media(tarefas.stream().map(TarefaEstatistica::duracaoMinutos).filter(Objects::nonNull).toList()));
    }

    private List<PorSprint> porSprint(List<Task> estimadas, List<Task> puladas, Map<Long, TarefaEstatistica> stats) {
        Map<String, List<TarefaEstatistica>> grupos = new TreeMap<>();
        estimadas.forEach(t -> grupos.computeIfAbsent(sprintDe(t), k -> new ArrayList<>()).add(stats.get(t.getId())));
        Map<String, Long> puladasPorSprint = puladas.stream()
                .collect(Collectors.groupingBy(this::sprintDe, Collectors.counting()));
        puladasPorSprint.keySet().forEach(s -> grupos.computeIfAbsent(s, k -> new ArrayList<>()));

        return grupos.entrySet().stream().map(e -> {
            Agregado a = agregar(e.getValue());
            return new PorSprint(e.getKey(), e.getValue().size(),
                    puladasPorSprint.getOrDefault(e.getKey(), 0L).intValue(),
                    a.totalPontos, a.mediaPontos, a.totalHoras, a.mediaHoras, a.mediaRodadas, a.taxaConsenso);
        }).toList();
    }

    private List<PorUsuario> porUsuario(Map<Long, List<Estimation>> votosPorTarefa, Map<Long, TarefaEstatistica> stats) {
        Map<String, List<Estimation>> porUsuario = votosPorTarefa.values().stream().flatMap(List::stream)
                .collect(Collectors.groupingBy(v -> v.getUsuario().getUsuario(), TreeMap::new, Collectors.toList()));

        return porUsuario.entrySet().stream().map(e -> {
            List<Estimation> votos = e.getValue();
            List<Estimation> comPontos = votos.stream().filter(v -> v.getPontos() != null && v.getPontos() > 0).toList();
            List<Double> horas = votos.stream().map(Estimation::getHoras).filter(h -> h != null && h > 0).toList();
            List<Double> horasTeste = votos.stream().map(Estimation::getHorasTeste).filter(h -> h != null && h > 0).toList();

            // Desvio com sinal em relação ao valor final da tarefa: positivo = estima acima
            List<Double> desvios = new ArrayList<>();
            long acertos = 0;
            for (Estimation v : comPontos) {
                TarefaEstatistica t = stats.get(v.getTaskId());
                Double referencia = t != null ? pontosReferencia(t) : null;
                if (referencia == null) continue;
                desvios.add(v.getPontos() - referencia);
                Integer cartaFinal = t.pontosFinais() != null ? t.pontosFinais() : t.modaPontos();
                if (v.getPontos().equals(cartaFinal)) acertos++;
            }

            return new PorUsuario(
                    e.getKey(),
                    votos.get(0).getUsuario().getTipoPerfil().name(),
                    (int) votos.stream().map(Estimation::getTaskId).distinct().count(),
                    comPontos.size(),
                    votos.stream().filter(v -> v.getPontos() != null && v.getPontos() == 0).count(),
                    media(comPontos.stream().map(Estimation::getPontos).toList()),
                    horas.size(),
                    media(horas),
                    horasTeste.size(),
                    media(horasTeste),
                    media(desvios),
                    comPontos.isEmpty() ? null : arred(100.0 * acertos / comPontos.size())
            );
        }).toList();
    }

    private Map<Integer, Long> distribuicaoPontos(Map<Long, List<Estimation>> votos) {
        return votos.values().stream().flatMap(List::stream)
                .map(Estimation::getPontos).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
    }

    private Map<Double, Long> distribuicaoHoras(Map<Long, List<Estimation>> votos) {
        return votos.values().stream().flatMap(List::stream)
                .map(Estimation::getHoras).filter(h -> h != null && h > 0)
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
    }

    private record Agregado(Double totalPontos, Double mediaPontos, Double totalHoras, Double mediaHoras,
                            Double mediaRodadas, Double taxaConsenso) {}

    /** Pontos que valem para a tarefa: o final do moderador ou, em tarefas antigas, a média dos votos. */
    private Double pontosReferencia(TarefaEstatistica t) {
        return t.pontosFinais() != null ? Double.valueOf(t.pontosFinais()) : t.mediaPontos();
    }

    private Double horasReferencia(TarefaEstatistica t) {
        return t.horasFinais() != null ? t.horasFinais() : t.mediaHoras();
    }

    private Agregado agregar(Collection<TarefaEstatistica> tarefas) {
        List<Double> pontos = tarefas.stream().map(this::pontosReferencia).filter(Objects::nonNull).toList();
        List<Double> horas = tarefas.stream().map(this::horasReferencia).filter(Objects::nonNull).toList();
        long comVotos = tarefas.stream().filter(t -> t.mediaPontos() != null).count();
        long consenso = tarefas.stream().filter(TarefaEstatistica::consenso).count();
        return new Agregado(
                pontos.isEmpty() ? null : arred(soma(pontos)),
                media(pontos),
                horas.isEmpty() ? null : arred(soma(horas)),
                media(horas),
                media(tarefas.stream().map(TarefaEstatistica::rodadas).toList()),
                comVotos == 0 ? null : arred(100.0 * consenso / comVotos)
        );
    }

    /** Valor mais votado; em empate, prevalece o maior (estimativa mais conservadora). */
    private Integer moda(List<Integer> valores) {
        return valores.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(null);
    }

    private double soma(List<? extends Number> valores) {
        return valores.stream().mapToDouble(Number::doubleValue).sum();
    }

    private Double media(List<? extends Number> valores) {
        return valores.isEmpty() ? null : arred(soma(valores) / valores.size());
    }

    private static Double arred(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
