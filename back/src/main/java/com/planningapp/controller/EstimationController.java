package com.planningapp.controller;

import com.planningapp.dto.EstimativaDTO;
import com.planningapp.dto.EstimativaHorasDTO;
import com.planningapp.entity.Estimation;
import com.planningapp.entity.Task;
import com.planningapp.notification.service.EstimationNotificationService;
import com.planningapp.service.EstimationService;
import com.planningapp.service.TaskService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tarefas/{taskId}/estimativas")
@CrossOrigin
public class EstimationController {
    @Autowired
    private EstimationService estimationService;
    @Autowired
    private TaskService taskService;@Autowired
    private EstimationNotificationService notificationService;
    
    @GetMapping("/task")
    public List<Estimation> getByTaskId(@PathVariable Long taskId) {
        return estimationService.findByTaskId(taskId);
    }

    @DeleteMapping("/excluirTarefa/{id}")
    public void delete(@PathVariable("id") Long id) {
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
        				  .filter(e -> e.getPontos() >= 0) // ignora votos "?"
        				  .mapToDouble(Estimation::getHoras)
                          .average()
                          .orElse(0.0);
    }
    
    
    @PostMapping("/votarHoras")  //está gravando zerado a hora
    public ResponseEntity<?> votarHoras(@PathVariable Long taskId, @RequestBody EstimativaHorasDTO dto) {
        Optional<Estimation> estOpt = estimationService.findByTaskIdAndParticipante(taskId, dto.getParticipante());
        
    	if (taskId == null) {
    		return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Task ID inválido."));
    	}
    	
        if (dto.getParticipante() == null || dto.getHoras() < 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Dados incompletos"));
        }

        if (estOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Estimativa não encontrada"));
        }

        Estimation est = estOpt.get();
        est.setHoras(dto.getHoras());
        estimationService.save(est);

        return ResponseEntity.ok(Map.of("success", true, "message", "Horas registradas"));
    } 
      
    
    
    @PostMapping("/votar")
    public ResponseEntity<?> votar(@PathVariable Long taskId, @RequestBody EstimativaDTO dto) {
    
    	
    	if (taskId == null || taskId <= 0) {
    		return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Task ID inválido."));
    	}
    	
        
        if (dto.getParticipante() == null || dto.getPontos() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Dados incompletos"));
        }
        
        Optional<Task> tarefaOpt = taskService.findById(taskId);
        
        if (tarefaOpt.isEmpty()) {
        	return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
        }
       
        
        List<Estimation> estimativas = estimationService.findByTaskId(taskId);
        
        if (!estimativas.isEmpty()) {
        	Estimation est = estimativas.get(0);
        	est.setPontos(dto.getPontos());
        	estimationService.save(est);
        }else {        	
	        Estimation estimativa = new Estimation();
	        estimativa.setTarefa(tarefaOpt.get());
	        estimativa.setParticipante(dto.getParticipante());
	        estimativa.setPontos(dto.getPontos());
	        estimativa.setRevealed(false); 
	        estimationService.save(estimativa);
        }
    	

        return ResponseEntity.ok(Map.of("success", true, "message", "Voto registrado"));
    }
    

    @GetMapping("/listar")
    public List<Map<String, Object>> listarEstimativas(@PathVariable("taskId") Long taskId) {
        List<Estimation> estimativas = estimationService.findByTaskId(taskId);
        
        return estimativas.stream().map(est -> {
            Map<String, Object> map = new HashMap<>();
            map.put("user", est.getParticipante());
            
            Object pontos = est.isRevealed() ? (est.getPontos() == -1 ? "?" : est.getPontos()) : "🔒";
            Object horas = est.isRevealed() ? est.getHoras() : "🔒";
            map.put("Pontos", pontos);
            map.put("Horas", horas);
            return map;
        }).collect(Collectors.toList());      
    }
    
    @PostMapping("/revelarPontos")
    public ResponseEntity<?> revelarPontos(@PathVariable Long taskId) {
        List<Estimation> estimativas = estimationService.findByTaskId(taskId);
        estimativas.forEach(est -> est.setRevealed(true));
        estimationService.saveAll(estimativas);
        notificationService.notificarTodos("REVELAR_PONTOS", taskId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Pontos revelados"));
    }
    
    @PostMapping("/revelar-horas")
    public ResponseEntity<?> revelar(@PathVariable Long taskId) {
        List<Estimation> estimativas = estimationService.findByTaskId(taskId);
        estimativas.forEach(est -> est.setHorasReveladas(true));
        estimationService.saveAll(estimativas);
        notificationService.notificarTodos("REVELAR_HORAS", taskId);
        return ResponseEntity.ok().build();
    }
    
    
    @PostMapping("/resetar")
    public void resetarVotacao(@PathVariable Long taskId) {
    	estimationService.deleteAll(estimationService.findByTaskId(taskId));
    }
    
    
    @GetMapping("/todos-votaram-pontos")
    public boolean todosVotaramPontos(@PathVariable Long taskId) {
        return estimationService.todosVotaramPontos(taskId);
    }
    
    @GetMapping("/todos-votaram-horas")
    public boolean todosVotaramHoras(@PathVariable Long taskId) {
        return estimationService.todosVotaramHoras(taskId);
    }
    
    
    @GetMapping("/resumo-votos")
    public List<Map<String, Object>> listarResumoVotos(@PathVariable("taskId") Long taskId) {
        List<Estimation> estimativas = estimationService.findByTaskId(taskId);

        return estimativas.stream().map(est -> {Map<String, Object> map = new HashMap<>();
        	map.put("participante", est.getParticipante());
        	map.put("pontos", est.isRevealed() ? (est.getPontos() == -1 ? "?" : est.getPontos()) : "🔒");
        	map.put("horas", est.isRevealed() ? (est.getHoras() == -1 ? "?" : est.getHoras()) : "🔒");
        	return map;
        }).collect(Collectors.toList());		
    }


    
    
}
