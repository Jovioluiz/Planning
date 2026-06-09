package com.planningapp.repository;

import com.planningapp.entity.Sala;
import com.planningapp.entity.SalaMembro;
import com.planningapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SalaMembroRepository extends JpaRepository<SalaMembro, Long> {
    List<SalaMembro> findBySala(Sala sala);
    boolean existsBySalaAndUsuario(Sala sala, User usuario);
    Optional<SalaMembro> findBySalaAndUsuario(Sala sala, User usuario);
    List<SalaMembro> findByUsuario(User usuario);

    @Transactional
    void deleteBySalaAndUsuario(Sala sala, User usuario);
}
