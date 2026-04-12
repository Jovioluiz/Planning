package com.planningapp.controller;

import com.planningapp.dto.TaskDTO;
import com.planningapp.entity.Task;
import com.planningapp.service.TaskService;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

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

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/importar")
    public ResponseEntity<?> importarTarefas(@Valid @RequestBody List<TaskDTO> dtos) {
        taskService.importarDTOs(dtos);
        return ResponseEntity.ok(Map.of("success", true, "message", "Tarefas importadas com sucesso"));
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

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/liberar")
    public ResponseEntity<?> liberarTarefa(@PathVariable("id") Long taskId) {
        boolean liberada = taskService.liberarTarefa(taskId);
        if (liberada) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa liberada"));
        } else {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/excluirTarefa/{id}")
    public ResponseEntity<?> excluirTarefa(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa removida"));
    }
}
