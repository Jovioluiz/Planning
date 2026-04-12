package com.planningapp.service;

import com.planningapp.entity.Estimation;
import com.planningapp.repository.EstimationRepository;
import com.planningapp.repository.TaskParticipantRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EstimationService {

    @Autowired
    private EstimationRepository estimationRepository;

    @Autowired
    private TaskParticipantRepository participantRepository;

    public List<Estimation> findAll() {
        return estimationRepository.findAll();
    }

    public List<Estimation> findByTaskId(Long taskId) {
        return estimationRepository.findByTaskId(taskId);
    }

    @Transactional
    public Estimation save(Estimation estimation) {
        return estimationRepository.save(estimation);
    }

    @Transactional
    public void saveAll(List<Estimation> estimations) {
        estimationRepository.saveAll(estimations);
    }

    @Transactional
    public void delete(Long id) {
        estimationRepository.deleteById(id);
    }

    @Transactional
    public void deleteAll(List<Estimation> estimations) {
        estimationRepository.deleteAll(estimations);
    }

    public Optional<Estimation> findByTaskIdAndParticipante(Long taskId, String participante) {
        return estimationRepository.findByTaskIdAndParticipante(taskId, participante);
    }

    /**
     * Todos os PARTICIPANTES VINCULADOS à tarefa votaram pontos.
     * Retorna false se não há participantes ou se algum ainda não votou.
     */
    public boolean todosVotaramPontos(Long taskId) {
        long totalParticipantes = participantRepository.countByTaskId(taskId);
        if (totalParticipantes == 0) return false;
        List<Estimation> estimativas = findByTaskId(taskId);
        long votaram = estimativas.stream().filter(e -> e.getPontos() != null).count();
        return votaram >= totalParticipantes;
    }

    /**
     * Todos os PARTICIPANTES VINCULADOS à tarefa votaram horas.
     * Retorna false se não há participantes ou se algum ainda não votou horas.
     */
    public boolean todosVotaramHoras(Long taskId) {
        long totalParticipantes = participantRepository.countByTaskId(taskId);
        if (totalParticipantes == 0) return false;
        List<Estimation> estimativas = findByTaskId(taskId);
        long votaramHoras = estimativas.stream()
                .filter(e -> e.getHoras() != null && e.getHoras() > 0)
                .count();
        return votaramHoras >= totalParticipantes;
    }
}
