package com.planningapp.controller;

import com.planningapp.dto.TaskDTO;
import com.planningapp.entity.Task;
import com.planningapp.notification.service.EstimationNotificationService;
import com.planningapp.service.TaskService;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private EstimationNotificationService notificationService;

    @GetMapping
    public List<Task> listarTarefas() {
        return taskService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return taskService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/importar")
    public ResponseEntity<?> importarTarefas(@Valid @RequestBody List<TaskDTO> dtos, Authentication auth) {
        if (!isAdmin(auth)) return forbidden();
        int inseridas = taskService.importarDTOs(dtos);
        int duplicadas = dtos.size() - inseridas;
        String msg = inseridas + " tarefa(s) importada(s)."
                + (duplicadas > 0 ? " " + duplicadas + " ignorada(s) por já existirem no banco." : "");
        return ResponseEntity.ok(Map.of("success", true, "message", msg, "inseridas", inseridas, "duplicadas", duplicadas));
    }

    @GetMapping("/liberadas")
    public ResponseEntity<List<Task>> listarLiberadas() {
        return ResponseEntity.ok(taskService.listarTarefasLiberadasParaEstimativa());
    }

    @GetMapping("/votadas")
    public ResponseEntity<List<Task>> listarVotadas() {
        return ResponseEntity.ok(taskService.listarTarefasJaVotadas());
    }

    @GetMapping("/fila")
    public List<Task> listarFilaEstimativas() {
        return taskService.findNaoEstimadasENaoLiberadas();
    }

    @GetMapping("/sprints")
    public List<String> listarSprints() {
        return taskService.listarSprints();
    }

    @GetMapping("/{id}/participantes")
    public List<String> getParticipantes(@PathVariable Long id) {
        return taskService.getParticipantes(id);
    }

    @PostMapping("/{id}/liberar-horas")
    public ResponseEntity<?> liberarHorasVotacao(@PathVariable Long id, Authentication auth) {
        if (!isAdmin(auth)) return forbidden();
        boolean ok = taskService.liberarHorasVotacao(id);
        if (ok) {
            notificationService.notificarTodos("HORAS_LIBERADAS", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Votação de horas liberada"));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
    }

    @PostMapping("/{id}/liberar")
    public ResponseEntity<?> liberarTarefa(@PathVariable("id") Long taskId, Authentication auth) {
        if (!isAdmin(auth)) return forbidden();
        boolean liberada = taskService.liberarTarefa(taskId);
        if (liberada) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa liberada"));
        } else {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<?> finalizarTarefa(@PathVariable Long id, Authentication auth) {
        if (!isAdmin(auth)) return forbidden();
        boolean ok = taskService.finalizarTarefa(id);
        if (ok) {
            notificationService.notificarTodos("TAREFA_FINALIZADA", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa finalizada"));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
    }

    @PostMapping("/{id}/pular")
    public ResponseEntity<?> pularTarefa(@PathVariable Long id, Authentication auth) {
        if (!isAdmin(auth)) return forbidden();
        boolean ok = taskService.pularTarefa(id);
        if (ok) {
            notificationService.notificarTodos("TAREFA_PULADA", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa devolvida para a fila"));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
    }

    @DeleteMapping("/{id}/participantes/{participante}")
    public ResponseEntity<?> removerParticipante(
            @PathVariable Long id,
            @PathVariable String participante,
            Authentication auth) {
        if (!isAdmin(auth)) return forbidden();
        taskService.removerParticipanteDaTarefa(id, participante);
        notificationService.notificarTodos("PARTICIPANTE_REMOVIDO", id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Participante removido da votação"));
    }

    @DeleteMapping("/excluirTarefa/{id}")
    public ResponseEntity<?> excluirTarefa(@PathVariable Long id, Authentication auth) {
        if (!isAdmin(auth)) return forbidden();
        taskService.delete(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa removida"));
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403)
                .body(Map.of("success", false, "message", "Acesso negado: apenas administradores"));
    }
}
