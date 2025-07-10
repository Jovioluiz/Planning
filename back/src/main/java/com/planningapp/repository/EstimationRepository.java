package com.planningapp.repository;

import com.planningapp.entity.Estimation;
import com.planningapp.entity.Task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstimationRepository extends JpaRepository<Estimation, Long> {
    List<Estimation> findByTaskId(Long taskId);
    boolean existsByTaskAndParticipante(Task task, String participante);
}
