package com.planningapp.controller;

import com.planningapp.entity.Task;
import com.planningapp.service.TaskService;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin
public class TaskController {
    @Autowired
    private TaskService taskService;

    @GetMapping
    public List<Task> listarTarefas() {
        return taskService.findAll();
    }

    @PostMapping("/criarTask")
    public Task create(@RequestBody Task task) {
        return taskService.save(task);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        taskService.delete(id);
    }
    
    @PostMapping("/importar")
    public ResponseEntity<?> importarTarefas(@RequestBody List<Task> tarefas) {
        taskService.saveAll(tarefas);
        return ResponseEntity.ok(Map.of("sucess", true,
        								"message", "tarefas importadas com sucesso"));
    }
    
    @GetMapping("/ativa")
    public ResponseEntity<List<Task>> tarefaAtiva() {
        return ResponseEntity.ok(taskService.listarTarefasLiberadasParaEstimativa());
    }
    
    @GetMapping("/liberadas")
    public ResponseEntity<List<Task>> listarLiberadas() {
        return ResponseEntity.ok(taskService.listarTarefasLiberadasParaEstimativa());
    }
    
    @GetMapping("/votadas")
    public ResponseEntity<List<Task>> listarVotadas() {
        return ResponseEntity.ok(taskService.listarTarefasJaVotadas());
    }
    
    //alterar para buscar pelo numero
    @PostMapping("/{id}/liberar")
    public ResponseEntity<?> liberarTarefa(@PathVariable("id") Long taskId) {
    	var liberada = taskService.liberarTarefa(taskId);
    	
    	if (liberada) {
    		return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa liberada"));
    	} else {
    		return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
    	}
    }
    
    @GetMapping("/fila")
    public List<Task> listarFilaEstimativas() {
        return taskService.findNaoEstimadasENaoLiberadas();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return taskService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
