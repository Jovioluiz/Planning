package com.planningapp.repository;

import com.planningapp.entity.TaskParticipant;
import com.planningapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TaskParticipantRepository extends JpaRepository<TaskParticipant, Long> {
    List<TaskParticipant> findByTaskId(Long taskId);
    long countByTaskId(Long taskId);
    boolean existsByTaskIdAndUsuario(Long taskId, User usuario);

    @Transactional
    void deleteByTaskId(Long taskId);

    @Transactional
    void deleteByTaskIdAndUsuario(Long taskId, User usuario);
}
