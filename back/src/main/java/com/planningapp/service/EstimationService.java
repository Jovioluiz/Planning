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
    
    public void saveAll(List<Estimation> estimation) {
    	for (Estimation est : estimation) {
            estimationRepository.save(est);
		}
    }

    public void delete(Long id) {
        estimationRepository.deleteById(id);
    }
    
    public void deleteAll(List<Estimation> estimation) {
    	for (Estimation est : estimation) {
    		delete(est.getId());
    	}        
    }
    
//    public boolean existsByTaskAndParticipante(Task task, String participante) {
//    	return estimationRepository.existsByTaskAndParticipante(task, participante);
//    }
    
    public Optional<Estimation> findByTaskIdAndParticipante(Long taskId, String participante) {
    	return estimationRepository.findByTaskIdAndParticipante(taskId, participante);
    }
   
}
