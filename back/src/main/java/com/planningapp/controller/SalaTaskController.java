package com.planningapp.controller;

import com.planningapp.dto.TaskDTO;
import com.planningapp.entity.Sala;
import com.planningapp.entity.Task;
import com.planningapp.notification.service.EstimationNotificationService;
import com.planningapp.service.SalaService;
import com.planningapp.service.TaskService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/salas/{salaId}/tasks")
public class SalaTaskController {

    @Autowired private TaskService taskService;
    @Autowired private SalaService salaService;
    @Autowired private EstimationNotificationService notificationService;

    private Sala getSala(Long salaId) {
        return salaService.findById(salaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala não encontrada"));
    }

    private boolean isModerador(Authentication auth, Long salaId) {
        return auth != null
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                && salaService.isModerador(salaId, auth.getName());
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("success", false, "message", "Acesso negado: você não é o moderador desta sala"));
    }

    @GetMapping
    public List<Task> listar(@PathVariable Long salaId) {
        return taskService.findBySala(getSala(salaId));
    }

    @GetMapping("/fila")
    public List<Task> fila(@PathVariable Long salaId) {
        return taskService.findNaoEstimadasENaoLiberadasSala(getSala(salaId));
    }

    @GetMapping("/liberadas")
    public ResponseEntity<List<Task>> liberadas(@PathVariable Long salaId) {
        return ResponseEntity.ok(taskService.listarLiberadasSala(getSala(salaId)));
    }

    @GetMapping("/votadas")
    public ResponseEntity<List<Task>> votadas(@PathVariable Long salaId) {
        return ResponseEntity.ok(taskService.listarVotadasSala(getSala(salaId)));
    }

    @GetMapping("/{id}/participantes")
    public List<String> participantes(@PathVariable Long salaId, @PathVariable Long id) {
        return taskService.getParticipantes(id);
    }

    @PostMapping("/importar")
    public ResponseEntity<?> importar(
            @PathVariable Long salaId,
            @Valid @RequestBody List<TaskDTO> dtos,
            Authentication auth) {
        if (!isModerador(auth, salaId)) return forbidden();
        Sala sala = getSala(salaId);
        int inseridas = taskService.importarDTOsSala(dtos, sala);
        int duplicadas = dtos.size() - inseridas;
        String msg = inseridas + " tarefa(s) importada(s)."
                + (duplicadas > 0 ? " " + duplicadas + " ignorada(s) por já existirem." : "");
        return ResponseEntity.ok(Map.of("success", true, "message", msg, "inseridas", inseridas, "duplicadas", duplicadas));
    }

    @PostMapping("/{id}/liberar")
    public ResponseEntity<?> liberar(
            @PathVariable Long salaId,
            @PathVariable Long id,
            Authentication auth) {
        if (!isModerador(auth, salaId)) return forbidden();
        boolean ok = taskService.liberarTarefaSala(id, getSala(salaId));
        if (ok) return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa liberada"));
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada nesta sala"));
    }

    @PostMapping("/{id}/liberar-horas")
    public ResponseEntity<?> liberarHoras(
            @PathVariable Long salaId,
            @PathVariable Long id,
            Authentication auth) {
        if (!isModerador(auth, salaId)) return forbidden();
        boolean ok = taskService.liberarHorasVotacao(id);
        if (ok) {
            notificationService.notificarSala(salaId, "HORAS_LIBERADAS", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Votação de horas liberada"));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
    }

    @PostMapping("/{id}/liberar-horas-teste")
    public ResponseEntity<?> liberarHorasTeste(
            @PathVariable Long salaId,
            @PathVariable Long id,
            Authentication auth) {
        if (!isModerador(auth, salaId)) return forbidden();
        boolean ok = taskService.liberarHorasTesteVotacao(id);
        if (ok) {
            notificationService.notificarSala(salaId, "HORAS_TESTE_LIBERADAS", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Votação de teste liberada"));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<?> finalizar(
            @PathVariable Long salaId,
            @PathVariable Long id,
            Authentication auth) {
        if (!isModerador(auth, salaId)) return forbidden();
        boolean ok = taskService.finalizarTarefa(id);
        if (ok) {
            notificationService.notificarSala(salaId, "TAREFA_FINALIZADA", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa finalizada"));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
    }

    @PostMapping("/{id}/pular")
    public ResponseEntity<?> pular(
            @PathVariable Long salaId,
            @PathVariable Long id,
            Authentication auth) {
        if (!isModerador(auth, salaId)) return forbidden();
        boolean ok = taskService.pularTarefa(id);
        if (ok) {
            notificationService.notificarSala(salaId, "TAREFA_PULADA", id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa devolvida para a fila"));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Tarefa não encontrada"));
    }

    @DeleteMapping("/{id}/participantes/{participante}")
    public ResponseEntity<?> removerParticipante(
            @PathVariable Long salaId,
            @PathVariable Long id,
            @PathVariable String participante,
            Authentication auth) {
        if (!isModerador(auth, salaId)) return forbidden();
        taskService.removerParticipanteDaTarefa(id, participante);
        notificationService.notificarSala(salaId, "PARTICIPANTE_REMOVIDO", id, Map.of("participante", participante));
        return ResponseEntity.ok(Map.of("success", true, "message", "Participante removido"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluir(
            @PathVariable Long salaId,
            @PathVariable Long id,
            Authentication auth) {
        if (!isModerador(auth, salaId)) return forbidden();
        taskService.delete(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Tarefa removida"));
    }
}
