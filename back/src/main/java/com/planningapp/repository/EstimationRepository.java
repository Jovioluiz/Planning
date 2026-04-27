package com.planningapp.repository;

import com.planningapp.entity.Estimation;
import com.planningapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EstimationRepository extends JpaRepository<Estimation, Long> {
    List<Estimation> findByTaskId(Long taskId);
    Optional<Estimation> findByTaskIdAndUsuario(Long taskId, User usuario);

    @Query("SELECT e FROM Estimation e WHERE e.task.id = :taskId AND COALESCE(e.rodada, 1) = :rodada")
    List<Estimation> findByTaskIdAndRodada(@Param("taskId") Long taskId, @Param("rodada") Integer rodada);

    @Query("SELECT e FROM Estimation e WHERE e.task.id = :taskId AND e.usuario = :usuario AND COALESCE(e.rodada, 1) = :rodada")
    Optional<Estimation> findByTaskIdAndUsuarioAndRodada(@Param("taskId") Long taskId, @Param("usuario") User usuario, @Param("rodada") Integer rodada);
}
