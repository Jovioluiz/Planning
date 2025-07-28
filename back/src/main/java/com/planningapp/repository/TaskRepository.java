package com.planningapp.repository;

import com.planningapp.entity.Task;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
	Optional<Task> findFirstByEstimadaFalseOrderByIdAsc();
	List<Task> findByEstimadaFalseAndLiberadaTrueOrderByNumeroAsc();
	List<Task> findByEstimadaFalseAndLiberadaFalseOrderByIdAsc();
	//Optional<Task> findById();
	List<Task> findByEstimadaFalseAndLiberadaTrueOrderByIdAsc();
	List<Task> findByEstimadaTrue();
}
