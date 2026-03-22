package com.planningapp.service;

import com.planningapp.entity.Estimation;
import com.planningapp.repository.EstimationRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EstimationService {

    @Autowired
    private EstimationRepository estimationRepository;

    public List<Estimation> findAll() {
        return estimationRepository.findAll();
    }

    public List<Estimation> findByTaskId(Long taskId) {
        return estimationRepository.findByTaskId(taskId);
    }

    public Estimation save(Estimation estimation) {
        return estimationRepository.save(estimation);
    }

    // CORRIGIDO: usa saveAll do repositório em vez de loop individual —
    // reduz N queries para 1 batch.
    public void saveAll(List<Estimation> estimations) {
        estimationRepository.saveAll(estimations);
    }

    public void delete(Long id) {
        estimationRepository.deleteById(id);
    }

    public void deleteAll(List<Estimation> estimations) {
        estimationRepository.deleteAll(estimations);
    }

    public Optional<Estimation> findByTaskIdAndParticipante(Long taskId, String participante) {
        return estimationRepository.findByTaskIdAndParticipante(taskId, participante);
    }

    // CORRIGIDO: usa != null em vez de > 0 para não excluir a carta "café" (pontos = 0).
    // Antes, quem votava café travava o "todos votaram" indefinidamente.
    public boolean todosVotaramPontos(Long taskId) {
        List<Estimation> estimativas = findByTaskId(taskId);
        if (estimativas.isEmpty()) return false;
        return estimativas.stream().allMatch(e -> e.getPontos() != null);
    }

    // CORRIGIDO: idem — usa != null e > 0 (horas 0 seria inválido, mas não null).
    public boolean todosVotaramHoras(Long taskId) {
        List<Estimation> estimativas = findByTaskId(taskId);
        if (estimativas.isEmpty()) return false;
        return estimativas.stream()
                .allMatch(e -> e.getHoras() != null && e.getHoras() > 0);
    }
}
