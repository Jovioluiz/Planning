package com.planningapp.service;

import com.planningapp.dto.TaskDTO;
import com.planningapp.entity.Estimation;
import com.planningapp.entity.Task;
import com.planningapp.entity.TaskParticipant;
import com.planningapp.entity.UserSprint;
import com.planningapp.entity.enums.TipoPerfil;
import com.planningapp.repository.EstimationRepository;
import com.planningapp.repository.TaskParticipantRepository;
import com.planningapp.repository.TaskRepository;
import com.planningapp.repository.UserRepository;
import com.planningapp.repository.UserSprintRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskParticipantRepository participantRepository;

    @Autowired
    private EstimationRepository estimationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSprintRepository userSprintRepository;

    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    @Transactional
    public Task save(Task task) {
        return taskRepository.save(task);
    }

    @Transactional
    public void delete(Long id) {
        participantRepository.deleteByTaskId(id);
        taskRepository.deleteById(id);
    }

    @Transactional
    public void saveAll(List<Task> tarefas) {
        taskRepository.saveAll(tarefas);
    }

    @Transactional
    public int importarDTOs(List<TaskDTO> dtos) {
        List<Task> novas = dtos.stream()
                .filter(dto -> !taskRepository.existsByNumero(dto.getNumero()))
                .map(dto -> {
                    Task task = new Task();
                    task.setNumero(dto.getNumero());
                    task.setTitulo(dto.getTitulo());
                    task.setDescricao(dto.getDescricao());
                    task.setPrioridade(dto.getPrioridade());
                    task.setStatus(dto.getStatus());
                    task.setSprint(dto.getSprint());
                    return task;
                })
                .toList();
        if (!novas.isEmpty()) {
            List<Task> salvas = taskRepository.saveAll(novas);
            for (Task t : salvas) {
                String sprint = t.getSprint();
                if (sprint != null && !sprint.isBlank()) {
                    // Vincula apenas quem já selecionou essa sprint
                    userSprintRepository.findBySprint(sprint)
                            .stream().map(us -> us.getUsuario().getUsuario())
                            .forEach(j -> adicionarParticipante(t.getId(), j));
                } else {
                    // Sem sprint: vincula todos os JOGADORs (compatibilidade)
                    userRepository.findByTipoPerfil(TipoPerfil.JOGADOR)
                            .forEach(u -> adicionarParticipante(t.getId(), u.getUsuario()));
                }
            }
        }
        return novas.size();
    }

    public Optional<Task> findById(Long id) {
        return taskRepository.findById(id);
    }

    public List<Task> listarTarefasLiberadasParaEstimativa() {
        return taskRepository.findByEstimadaFalseAndLiberadaTrueOrderByIdAsc();
    }

    @Transactional
    public boolean liberarTarefa(Long id) {
        return taskRepository.findById(id).map(task -> {
            task.setLiberada(true);
            task.setLiberadaEm(Instant.now());
            taskRepository.save(task);
            String sprint = task.getSprint();
            if (sprint != null && !sprint.isBlank()) {
                userSprintRepository.findBySprint(sprint)
                        .stream().map(us -> us.getUsuario().getUsuario())
                        .forEach(u -> adicionarParticipante(id, u));
            } else {
                userRepository.findByTipoPerfil(TipoPerfil.JOGADOR)
                        .forEach(u -> adicionarParticipante(id, u.getUsuario()));
            }
            return true;
        }).orElse(false);
    }

    public List<Task> findNaoEstimadasENaoLiberadas() {
        return taskRepository.findByEstimadaFalseAndLiberadaFalseOrderByIdAsc();
    }

    public List<Task> listarTarefasJaVotadas() {
        return taskRepository.findByEstimadaTrue();
    }

    @Transactional
    public boolean finalizarTarefa(Long id) {
        return taskRepository.findById(id).map(task -> {
            task.setEstimada(true);
            task.setLiberada(false);
            taskRepository.save(task);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean liberarHorasVotacao(Long id) {
        return taskRepository.findById(id).map(task -> {
            task.setHorasLiberadas(true);
            taskRepository.save(task);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean pularTarefa(Long id) {
        return taskRepository.findById(id).map(task -> {
            task.setLiberada(false);
            task.setPontosRevelados(false);
            task.setHorasReveladas(false);
            task.setHorasLiberadas(false);
            task.setLiberadaEm(null);
            taskRepository.save(task);
            List<Estimation> votos = estimationRepository.findByTaskId(id);
            estimationRepository.deleteAll(votos);
            return true;
        }).orElse(false);
    }

    // ─── Participantes esperados ─────────────────────────────

    @Transactional
    public void removerParticipanteDaTarefa(Long taskId, String participante) {
        userRepository.findByUsuario(participante).ifPresent(user -> {
            participantRepository.deleteByTaskIdAndUsuario(taskId, user);
            estimationRepository.findByTaskIdAndUsuario(taskId, user)
                    .ifPresent(estimationRepository::delete);
        });
    }

    @Transactional
    public void adicionarParticipante(Long taskId, String participante) {
        userRepository.findByUsuario(participante).ifPresent(user -> {
            if (!participantRepository.existsByTaskIdAndUsuario(taskId, user)) {
                participantRepository.save(new TaskParticipant(taskId, user));
            }
        });
    }

    @Transactional
    public void vincularJogadorATarefasAtivas(String participante) {
        taskRepository.findAll().stream()
                .filter(t -> !t.getEstimada())
                .forEach(t -> adicionarParticipante(t.getId(), participante));
    }

    @Transactional
    public void vincularJogadorASprint(String participante, String sprint) {
        userRepository.findByUsuario(participante).ifPresent(user -> {
            if (!userSprintRepository.existsByUsuarioAndSprint(user, sprint)) {
                userSprintRepository.save(new UserSprint(user, sprint));
            }
        });
        taskRepository.findByEstimadaFalseAndSprint(sprint)
                .forEach(t -> adicionarParticipante(t.getId(), participante));
    }

    public List<String> listarSprints() {
        return taskRepository.findDistinctSprints();
    }

    public List<String> getParticipantes(Long taskId) {
        return participantRepository.findByTaskId(taskId)
                .stream()
                .map(tp -> tp.getUsuario().getUsuario())
                .toList();
    }
}
