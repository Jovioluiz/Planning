package com.planningapp.repository;

import com.planningapp.entity.Estimation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EstimationRepository extends JpaRepository<Estimation, Long> {
    List<Estimation> findByTaskId(Long taskId);
//    boolean existsByTaskAndParticipante(Task task, String participante);
    Optional<Estimation> findByTaskIdAndParticipante(Long taskId, String participante);
}
