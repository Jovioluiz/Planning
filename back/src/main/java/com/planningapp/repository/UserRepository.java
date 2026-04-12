package com.planningapp.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.planningapp.entity.User;
import com.planningapp.entity.enums.TipoPerfil;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsuario(String usuario);
    List<User> findByTipoPerfil(TipoPerfil tipoPerfil);
}
