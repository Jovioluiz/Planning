package com.planningapp.service;

import com.planningapp.dto.TaskDTO;
import com.planningapp.entity.Task;
import com.planningapp.repository.TaskRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    @Transactional
    public Task save(Task task) {
        return taskRepository.save(task);
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.deleteById(id);
    }

    @Transactional
    public void saveAll(List<Task> tarefas) {
        taskRepository.saveAll(tarefas);
    }

    @Transactional
    public void importarDTOs(List<TaskDTO> dtos) {
        List<Task> tarefas = dtos.stream().map(dto -> {
            Task task = new Task();
            task.setNumero(dto.getNumero());
            task.setTitulo(dto.getTitulo());
            task.setDescricao(dto.getDescricao());
            task.setPrioridade(dto.getPrioridade());
            task.setStatus(dto.getStatus());
            return task;
        }).toList();
        taskRepository.saveAll(tarefas);
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
            taskRepository.save(task);
            return true;
        }).orElse(false);
    }

    public List<Task> findNaoEstimadasENaoLiberadas() {
        return taskRepository.findByEstimadaFalseAndLiberadaFalseOrderByIdAsc();
    }

    public List<Task> listarTarefasJaVotadas() {
        return taskRepository.findByEstimadaTrue();
    }
}
