package com.planningapp.repository;

import com.planningapp.entity.TaskParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TaskParticipantRepository extends JpaRepository<TaskParticipant, Long> {
    List<TaskParticipant> findByTaskId(Long taskId);
    long countByTaskId(Long taskId);
    boolean existsByTaskIdAndParticipante(Long taskId, String participante);

    @Transactional
    void deleteByTaskId(Long taskId);

    @Transactional
    void deleteByTaskIdAndParticipante(Long taskId, String participante);
}
