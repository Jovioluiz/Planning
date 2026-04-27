package com.planningapp.repository;

import com.planningapp.entity.User;
import com.planningapp.entity.UserSprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSprintRepository extends JpaRepository<UserSprint, Long> {
    List<UserSprint> findBySprint(String sprint);
    boolean existsByUsuarioAndSprint(User usuario, String sprint);
}
