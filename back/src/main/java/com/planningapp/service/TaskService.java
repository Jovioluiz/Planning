package com.planningapp.service;

import com.planningapp.dto.TaskDTO;
import com.planningapp.entity.Estimation;
import com.planningapp.entity.Task;
import com.planningapp.entity.TaskParticipant;
import com.planningapp.entity.User;
import com.planningapp.entity.enums.TipoPerfil;
import com.planningapp.repository.EstimationRepository;
import com.planningapp.repository.TaskParticipantRepository;
import com.planningapp.repository.TaskRepository;
import com.planningapp.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                    return task;
                })
                .toList();
        if (!novas.isEmpty()) {
            List<Task> salvas = taskRepository.saveAll(novas);
            // Vincula todos os JOGADORs já cadastrados às novas tarefas
            List<String> jogadores = userRepository.findByTipoPerfil(TipoPerfil.JOGADOR)
                    .stream().map(User::getUsuario).toList();
            for (Task t : salvas) {
                for (String j : jogadores) {
                    adicionarParticipante(t.getId(), j);
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

    /**
     * Libera a tarefa para votação e vincula todos os JOGADOR ativos como participantes esperados.
     */
    @Transactional
    public boolean liberarTarefa(Long id) {
        return taskRepository.findById(id).map(task -> {
            task.setLiberada(true);
            taskRepository.save(task);
            // Vincula todos os JOGADORs registrados como participantes esperados desta tarefa
            List<User> jogadores = userRepository.findByTipoPerfil(TipoPerfil.JOGADOR);
            jogadores.forEach(u -> adicionarParticipante(id, u.getUsuario()));
            return true;
        }).orElse(false);
    }

    public List<Task> findNaoEstimadasENaoLiberadas() {
        return taskRepository.findByEstimadaFalseAndLiberadaFalseOrderByIdAsc();
    }

    public List<Task> listarTarefasJaVotadas() {
        return taskRepository.findByEstimadaTrue();
    }

    /**
     * Marca a tarefa como estimada (finaliza a votação com resultado gravado).
     */
    @Transactional
    public boolean finalizarTarefa(Long id) {
        return taskRepository.findById(id).map(task -> {
            task.setEstimada(true);
            task.setLiberada(false);
            taskRepository.save(task);
            return true;
        }).orElse(false);
    }

    /**
     * Passa a tarefa para frente sem estimar: remove da votação, limpa votos e flags,
     * devolvendo-a para a fila para ser votada futuramente.
     */
    @Transactional
    public boolean pularTarefa(Long id) {
        return taskRepository.findById(id).map(task -> {
            task.setLiberada(false);
            task.setPontosRevelados(false);
            task.setHorasReveladas(false);
            taskRepository.save(task);
            // Limpa os votos para que a tarefa possa ser votada novamente
            List<Estimation> votos = estimationRepository.findByTaskId(id);
            estimationRepository.deleteAll(votos);
            return true;
        }).orElse(false);
    }

    // ─── Participantes esperados ─────────────────────────────

    /**
     * Vincula um usuário como participante esperado de uma tarefa.
     * Ignora silenciosamente se o vínculo já existir.
     */
    @Transactional
    public void adicionarParticipante(Long taskId, String participante) {
        if (!participantRepository.existsByTaskIdAndParticipante(taskId, participante)) {
            participantRepository.save(new TaskParticipant(taskId, participante));
        }
    }

    /**
     * Vincula um JOGADOR a todas as tarefas ainda não estimadas (fila + liberadas).
     * Chamado no login para que quem entra em qualquer momento da sessão seja incluído.
     */
    @Transactional
    public void vincularJogadorATarefasAtivas(String participante) {
        taskRepository.findAll().stream()
                .filter(t -> !t.getEstimada())
                .forEach(t -> adicionarParticipante(t.getId(), participante));
    }

    /**
     * Retorna os nomes dos participantes esperados de uma tarefa.
     */
    public List<String> getParticipantes(Long taskId) {
        return participantRepository.findByTaskId(taskId)
                .stream()
                .map(TaskParticipant::getParticipante)
                .toList();
    }
}
