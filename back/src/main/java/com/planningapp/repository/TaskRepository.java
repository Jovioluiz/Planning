package com.planningapp.repository;

import com.planningapp.entity.Task;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Optional<Task> findFirstByEstimadaFalseOrderByIdAsc();
    List<Task> findByEstimadaFalseAndLiberadaTrueOrderByNumeroAsc();
    List<Task> findByEstimadaFalseAndLiberadaFalseOrderByIdAsc();
    List<Task> findByEstimadaFalseAndLiberadaTrueOrderByIdAsc();
    List<Task> findByEstimadaTrue();
    boolean existsByNumero(Long numero);

    List<Task> findByEstimadaFalseAndSprint(String sprint);

    @Query(value = "SELECT DISTINCT sprint FROM tarefas WHERE sprint IS NOT NULL AND estimada = false ORDER BY sprint", nativeQuery = true)
    List<String> findDistinctSprints();
}
