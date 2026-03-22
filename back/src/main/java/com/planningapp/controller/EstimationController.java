package com.planningapp.controller;

import com.planningapp.dto.EstimativaDTO;
import com.planningapp.dto.EstimativaHorasDTO;
import com.planningapp.dto.EstimativaResponseDTO;
import com.planningapp.entity.Estimation;
import com.planningapp.entity.Task;
import com.planningapp.notification.service.EstimationNotificationService;
import com.planningapp.service.EstimationService;
import com.planningapp.service.TaskService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// CORRIGIDO: removido @CrossOrigin — CORS centralizado em WebConfig.
@RestController
@RequestMapping("/api/tarefas/{taskId}/estimativas")
public class EstimationController {

    @Autowired private EstimationService estimationService;
    @Autowired private TaskService taskService;
    @Autowired private EstimationNotificationService notificationService;

    // CORRIGIDO: endpoint /listar agora usa EstimativaResponseDTO
    // com os mesmos campos de /resumo-votos — resposta consistente.
    @GetMapping("/listar")
    public List<EstimativaResponseDTO> listarEstimativas(@PathVariable Long taskId) {
        return estimationService.findByTaskId(taskId).stream()
                .map(est -> {
                    Object pontos = est.isRevealed()
                            ? (est.getPontos() != null && est.getPontos() == -1 ? "?" : est.getPontos())
                            : "🔒";
                    Object horas = est.isRevealed()
                            ? (est.getHoras() != null && est.getHoras() == -1 ? "?" : est.getHoras())
                            : "🔒";
                    return new EstimativaResponseDTO(est.getParticipante(), pontos, horas, est.isRevealed());
                })
                .collect(Collectors.toList());
    }

    // CORRIGIDO: /resumo-votos usa o mesmo DTO — resposta idêntica ao /listar.
    @GetMapping("/resumo-votos")
    public List<EstimativaResponseDTO> listarResumoVotos(@PathVariable Long taskId) {
        return listarEstimativas(taskId);
    }

    @PostMapping("/votar")
    public ResponseEntity<?> votar(@PathVariable Long taskId, @RequestBody EstimativaDTO dto) {

        if (dto.getParticipante() == null || dto.getPontos() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Dados incompletos"));
        }

        Optional<Task> tarefaOpt = taskService.findById(taskId);
        if (tarefaOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }

        // CORRIGIDO: busca a estimativa do participante específico —
        // antes buscava todas e atualizava a primeira (sobrescrevia voto de outro participante).
        Optional<Estimation> existente =
                estimationService.findByTaskIdAndParticipante(taskId, dto.getParticipante());

        if (existente.isPresent()) {
            // Participante já votou — retorna 409 para o frontend tratar
            return ResponseEntity.status(409)
                    .body(Map.of("success", false, "message", "Você já votou nesta tarefa."));
        }

        Estimation estimativa = new Estimation();
        estimativa.setTarefa(tarefaOpt.get());
        estimativa.setParticipante(dto.getParticipante());
        estimativa.setPontos(dto.getPontos());
        estimativa.setRevealed(false);
        estimationService.save(estimativa);

        return ResponseEntity.ok(Map.of("success", true, "message", "Voto registrado"));
    }

    @PostMapping("/votarHoras")
    public ResponseEntity<?> votarHoras(@PathVariable Long taskId, @RequestBody EstimativaHorasDTO dto) {

        // CORRIGIDO: validações movidas para ANTES de usar taskId e dto —
        // antes o check de taskId == null vinha depois de usá-lo na query.
        if (dto.getParticipante() == null || dto.getHoras() == null || dto.getHoras() < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Dados incompletos ou inválidos"));
        }

        Optional<Estimation> estOpt =
                estimationService.findByTaskIdAndParticipante(taskId, dto.getParticipante());

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
        List<Estimation> estimativas = estimationService.findByTaskId(taskId);
        estimativas.forEach(est -> est.setRevealed(true));
        estimationService.saveAll(estimativas);

        // Atualiza flag na tarefa
        taskService.findById(taskId).ifPresent(task -> {
            task.setPontosRevelados(true);
            taskService.save(task);
        });

        notificationService.notificarTodos("REVELAR_PONTOS", taskId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Pontos revelados"));
    }

    @PostMapping("/revelar-horas")
    public ResponseEntity<?> revelarHoras(@PathVariable Long taskId) {
        List<Estimation> estimativas = estimationService.findByTaskId(taskId);
        estimativas.forEach(est -> est.setHorasReveladas(true));
        estimationService.saveAll(estimativas);

        // Atualiza flag na tarefa
        taskService.findById(taskId).ifPresent(task -> {
            task.setHorasReveladas(true);
            taskService.save(task);
        });

        notificationService.notificarTodos("REVELAR_HORAS", taskId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Horas reveladas"));
    }

    @PostMapping("/resetar")
    public ResponseEntity<?> resetarVotacao(@PathVariable Long taskId) {
        estimationService.deleteAll(estimationService.findByTaskId(taskId));

        // Reseta flags na tarefa
        taskService.findById(taskId).ifPresent(task -> {
            task.setPontosRevelados(false);
            task.setHorasReveladas(false);
            taskService.save(task);
        });

        return ResponseEntity.ok(Map.of("success", true, "message", "Votação resetada"));
    }

    @GetMapping("/todos-votaram-pontos")
    public boolean todosVotaramPontos(@PathVariable Long taskId) {
        return estimationService.todosVotaramPontos(taskId);
    }

    @GetMapping("/todos-votaram-horas")
    public boolean todosVotaramHoras(@PathVariable Long taskId) {
        return estimationService.todosVotaramHoras(taskId);
    }

    @DeleteMapping("/excluirTarefa/{id}")
    public void delete(@PathVariable("id") Long id) {
        estimationService.delete(id);
    }
}
