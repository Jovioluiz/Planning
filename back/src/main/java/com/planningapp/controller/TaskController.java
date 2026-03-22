package com.planningapp.controller;

import com.planningapp.dto.TaskDTO;
import com.planningapp.entity.Task;
import com.planningapp.service.TaskService;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// CORRIGIDO: removido @CrossOrigin — CORS centralizado em WebConfig.
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

    // CORRIGIDO: recebe TaskDTO em vez de expor a entidade JPA diretamente ao cliente.
    @PostMapping("/importar")
    public ResponseEntity<?> importarTarefas(@RequestBody List<TaskDTO> dtos) {
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

    @PostMapping("/{id}/liberar")
    public ResponseEntity<?> liberarTarefa(@PathVariable("id") Long taskId) {
        boolean liberada = taskService.liberarTarefa(taskId);
        if (liberada) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa liberada"));
        } else {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }
    }

    // CORRIGIDO: rota alinhada com o que o frontend chama (DELETE /api/tasks/excluirTarefa/{id})
    @DeleteMapping("/excluirTarefa/{id}")
    public ResponseEntity<?> excluirTarefa(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa removida"));
    }

    // Mantida a rota original também para compatibilidade
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa removida"));
    }
}
