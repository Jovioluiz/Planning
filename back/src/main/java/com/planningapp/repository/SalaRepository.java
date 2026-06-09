package com.planningapp.repository;

import com.planningapp.entity.Sala;
import com.planningapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalaRepository extends JpaRepository<Sala, Long> {
    Optional<Sala> findByCodigo(String codigo);
    List<Sala> findByModerador(User moderador);
    List<Sala> findByModeradorOrderByIdDesc(User moderador);
}
