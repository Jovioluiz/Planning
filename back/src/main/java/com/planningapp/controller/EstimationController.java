package com.planningapp.controller;

import com.planningapp.dto.EstimativaDTO;
import com.planningapp.entity.Estimation;
import com.planningapp.entity.Task;
import com.planningapp.service.EstimationService;
import com.planningapp.service.TaskService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tarefas/{taskId}/estimativas")
@CrossOrigin
public class EstimationController {
    @Autowired
    private EstimationService estimationService;
    @Autowired
    private TaskService taskService;
    
    @GetMapping("/task")
    public List<Estimation> getByTaskId(@PathVariable Long taskId) {
        return estimationService.findByTaskId(taskId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        estimationService.delete(id);
    }
    
    @GetMapping("/media")
    public double calcularMedia(@PathVariable Long taskId) {
        List<Estimation> estimativas = estimationService.findByTaskId(taskId);
        
        for (Estimation est : estimativas) {
			if (!est.isRevealed()) {
				return 0;
			}
		}
        
        return estimativas.stream()
                          .mapToDouble(Estimation::getHoras)
                          .average()
                          .orElse(0.0);
    }
    
    @PostMapping("/votar")
    public ResponseEntity<?> votar(@PathVariable Long taskId, @RequestBody EstimativaDTO dto) {
    
    	
    	if (taskId == null || taskId <= 0) {
    		return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Task ID inválido."));
    	}
    	
        
        if (dto.getParticipante() == null || dto.getPontos() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Dados incompletos"));
        }
        
        Optional<Task> tarefaOpt = taskService.findById(taskId);
        
        if (tarefaOpt.isEmpty()) {
        	return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }
        
        Task task = tarefaOpt.get();
        
        boolean jaVotou = estimationService.existsByTaskAndParticipante(task, dto.getParticipante());
        if (jaVotou) {
            return ResponseEntity.status(409).body(Map.of("success", false, "message", "Participante já votou nesta tarefa"));
        }
    	
        Estimation estimativa = new Estimation();
        estimativa.setTarefa(tarefaOpt.get());
        estimativa.setParticipante(dto.getParticipante());
        estimativa.setPontos(dto.getPontos());
        estimativa.setRevealed(false); // ou conforme regra

        estimationService.save(estimativa);

        return ResponseEntity.ok(Map.of("success", true, "message", "Voto registrado"));
    }
    
    @GetMapping("/listar")
    public ResponseEntity<List<Estimation>> listar(@PathVariable Long id) {
        return ResponseEntity.ok(estimationService.findByTaskId(id));
    }
//    public List<Map<String, Object>> listarEstimativas(@PathVariable Long taskId) {
//        List<Estimation> estimativas = estimationService.findByTaskId(taskId);
//        return estimativas.stream().map(est -> {
//            Map<String, Object> map = new HashMap<>();
//            map.put("user", est.getParticipante());
//            map.put("Pontos", est.isRevealed() ? est.getPontos() : "🔒");
//            map.put("Horas", est.isRevealed() ? est.getHoras() : "🔒");
//            return map;
//        }).collect(Collectors.toList());
//    }
    
    @PostMapping("/revelar")
    public void revelarEstimativas(@PathVariable Long taskId) {
        List<Estimation> estimativas = estimationService.findByTaskId(taskId);
        estimativas.forEach(est -> est.setRevealed(true));
        estimationService.saveAll(estimativas);
    }
    
    @PostMapping("/resetar")
    public void resetarVotacao(@PathVariable Long taskId) {
    	estimationService.deleteAll(estimationService.findByTaskId(taskId));
    }
    
    
}
