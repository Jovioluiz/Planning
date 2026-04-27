package com.planningapp.controller;

import com.planningapp.dto.EstimativaDTO;
import com.planningapp.dto.EstimativaHorasDTO;
import com.planningapp.dto.EstimativaResponseDTO;
import com.planningapp.entity.Estimation;
import com.planningapp.entity.Task;
import com.planningapp.notification.service.EstimationNotificationService;
import com.planningapp.repository.UserRepository;
import com.planningapp.service.EstimationService;
import com.planningapp.service.TaskService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tarefas/{taskId}/estimativas")
public class EstimationController {

    @Autowired private EstimationService estimationService;
    @Autowired private TaskService taskService;
    @Autowired private EstimationNotificationService notificationService;
    @Autowired private UserRepository userRepository;

    @GetMapping("/listar")
    public List<EstimativaResponseDTO> listarEstimativas(@PathVariable Long taskId) {
        return estimationService.findByTaskId(taskId).stream()
                .map(est -> {
                    Object pontos = est.isRevealed()
                            ? (est.getPontos() != null && est.getPontos() == 0 ? "☕" : est.getPontos())
                            : "🔒";
                    boolean horasRev = Boolean.TRUE.equals(est.isHorasReveladas());
                    Object horas = horasRev
                            ? est.getHoras()
                            : (est.getHoras() != null ? "🔒" : null);
                    return new EstimativaResponseDTO(est.getUsuario().getUsuario(), pontos, horas, est.isRevealed(), horasRev, est.getRodada());
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/resumo-votos")
    public List<EstimativaResponseDTO> listarResumoVotos(@PathVariable Long taskId) {
        return listarEstimativas(taskId);
    }

    @PostMapping("/votar")
    public ResponseEntity<?> votar(@PathVariable Long taskId, @Valid @RequestBody EstimativaDTO dto) {
        Optional<Task> tarefaOpt = taskService.findById(taskId);
        if (tarefaOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }

        Task task = tarefaOpt.get();
        if (!task.isLiberada()) {
            return ResponseEntity.status(400)
                    .body(Map.of("success", false, "message", "Esta tarefa não está liberada para votação"));
        }

        var userOpt = userRepository.findByUsuario(dto.getParticipante());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("success", false, "message", "Usuário não encontrado"));
        }

        int rodadaAtual = task.getRodadaAtual();

        Optional<Estimation> existente =
                estimationService.findByTaskIdAndParticipanteAndRodada(taskId, dto.getParticipante(), rodadaAtual);

        if (existente.isPresent()) {
            return ResponseEntity.status(409)
                    .body(Map.of("success", false, "message", "Você já votou nesta tarefa."));
        }

        Estimation estimativa = new Estimation();
        estimativa.setTarefa(task);
        estimativa.setUsuario(userOpt.get());
        estimativa.setPontos(dto.getPontos());
        estimativa.setRevealed(false);
        estimativa.setRodada(rodadaAtual);
        estimationService.save(estimativa);

        return ResponseEntity.ok(Map.of("success", true, "message", "Voto registrado"));
    }

    @PostMapping("/votarHoras")
    public ResponseEntity<?> votarHoras(@PathVariable Long taskId, @Valid @RequestBody EstimativaHorasDTO dto) {
        Optional<Task> tarefaOpt = taskService.findById(taskId);
        if (tarefaOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }

        int rodadaAtual = tarefaOpt.get().getRodadaAtual();

        Optional<Estimation> estOpt =
                estimationService.findByTaskIdAndParticipanteAndRodada(taskId, dto.getParticipante(), rodadaAtual);

        if (estOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("success", false, "message", "Estimativa de pontos não encontrada — vote nos pontos primeiro"));
        }

        Estimation est = estOpt.get();
        est.setHoras(dto.getHoras());
        estimationService.save(est);

        return ResponseEntity.ok(Map.of("success", true, "message", "Horas registradas"));
    }

    @PostMapping("/revelarPontos")
    public ResponseEntity<?> revelarPontos(@PathVariable Long taskId) {
        Optional<Task> tarefaOpt = taskService.findById(taskId);
        if (tarefaOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }

        int rodadaAtual = tarefaOpt.get().getRodadaAtual();
        List<Estimation> estimativas = estimationService.findByTaskIdAndRodada(taskId, rodadaAtual);
        estimativas.forEach(est -> est.setRevealed(true));
        estimationService.saveAll(estimativas);

        taskService.findById(taskId).ifPresent(task -> {
            task.setPontosRevelados(true);
            taskService.save(task);
        });

        notificationService.notificarTodos("REVELAR_PONTOS", taskId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Pontos revelados"));
    }

    @PostMapping("/revelar-horas")
    public ResponseEntity<?> revelarHoras(@PathVariable Long taskId) {
        Optional<Task> tarefaOpt = taskService.findById(taskId);
        if (tarefaOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }

        int rodadaAtual = tarefaOpt.get().getRodadaAtual();
        List<Estimation> estimativas = estimationService.findByTaskIdAndRodada(taskId, rodadaAtual);
        estimativas.forEach(est -> est.setHorasReveladas(true));
        estimationService.saveAll(estimativas);

        taskService.findById(taskId).ifPresent(task -> {
            task.setHorasReveladas(true);
            taskService.save(task);
        });

        notificationService.notificarTodos("REVELAR_HORAS", taskId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Horas reveladas"));
    }

    @PostMapping("/nova-rodada")
    public ResponseEntity<?> novaRodada(@PathVariable Long taskId) {
        Optional<Task> tarefaOpt = taskService.findById(taskId);
        if (tarefaOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }

        Task task = tarefaOpt.get();
        task.setRodadaAtual(task.getRodadaAtual() + 1);
        task.setPontosRevelados(false);
        task.setHorasLiberadas(false);
        taskService.save(task);

        notificationService.notificarTodos("NOVA_RODADA", taskId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Nova rodada iniciada", "rodada", task.getRodadaAtual()));
    }

    @PostMapping("/resetar")
    public ResponseEntity<?> resetarVotacao(@PathVariable Long taskId) {
        estimationService.deleteAll(estimationService.findByTaskId(taskId));

        taskService.findById(taskId).ifPresent(task -> {
            task.setPontosRevelados(false);
            task.setHorasReveladas(false);
            task.setHorasLiberadas(false);
            task.setRodadaAtual(1);
            taskService.save(task);
        });

        return ResponseEntity.ok(Map.of("success", true, "message", "Votação resetada"));
    }

    @GetMapping("/todos-votaram-pontos")
    public boolean todosVotaramPontos(@PathVariable Long taskId) {
        Optional<Task> taskOpt = taskService.findById(taskId);
        if (taskOpt.isEmpty()) return false;
        return estimationService.todosVotaramPontos(taskId, taskOpt.get().getRodadaAtual());
    }

    @GetMapping("/todos-votaram-horas")
    public boolean todosVotaramHoras(@PathVariable Long taskId) {
        Optional<Task> taskOpt = taskService.findById(taskId);
        if (taskOpt.isEmpty()) return false;
        return estimationService.todosVotaramHoras(taskId, taskOpt.get().getRodadaAtual());
    }

    @DeleteMapping("/excluirTarefa/{id}")
    public void delete(@PathVariable("id") Long id) {
        estimationService.delete(id);
    }
}
