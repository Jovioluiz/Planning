package com.planningapp.service;

import com.planningapp.dto.SalaDTO;
import com.planningapp.entity.*;
import com.planningapp.repository.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SalaService {

    @Autowired private SalaRepository salaRepository;
    @Autowired private SalaMembroRepository salaMembroRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskParticipantRepository participantRepository;

    @Transactional
    public Sala criarSala(String nome, String moderadorUsername) {
        User moderador = userRepository.findByUsuario(moderadorUsername)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        Sala sala = new Sala();
        sala.setNome(nome);
        sala.setCodigo(UUID.randomUUID().toString());
        sala.setModerador(moderador);
        sala.setAtiva(true);
        sala.setCriadaEm(Instant.now());
        return salaRepository.save(sala);
    }

    @Transactional
    public Sala entrarNaSala(String codigo, String usuarioUsername) {
        Sala sala = salaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RuntimeException("Sala não encontrada"));
        if (!sala.isAtiva()) throw new RuntimeException("Sala inativa");

        User usuario = userRepository.findByUsuario(usuarioUsername)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (!salaMembroRepository.existsBySalaAndUsuario(sala, usuario)) {
            salaMembroRepository.save(new SalaMembro(sala, usuario));
            // Vincula o novo membro às tarefas não estimadas da sala
            taskRepository.findBySalaAndEstimadaFalseAndLiberadaFalseOrderByIdAsc(sala)
                    .forEach(t -> adicionarParticipante(t.getId(), usuario));
            taskRepository.findBySalaAndEstimadaFalseAndLiberadaTrueOrderByIdAsc(sala)
                    .forEach(t -> adicionarParticipante(t.getId(), usuario));
        }

        return sala;
    }

    public boolean isModerador(Long salaId, String username) {
        return salaRepository.findById(salaId)
                .map(s -> s.getModerador().getUsuario().equals(username))
                .orElse(false);
    }

    public Optional<Sala> findByCodigo(String codigo) {
        return salaRepository.findByCodigo(codigo);
    }

    public Optional<Sala> findById(Long id) {
        return salaRepository.findById(id);
    }

    public List<Sala> findByModerador(String username) {
        return userRepository.findByUsuario(username)
                .map(salaRepository::findByModerador)
                .orElse(List.of());
    }

    @Transactional
    public List<Sala> buscarSalasComReativacao(String username) {
        userRepository.findByUsuario(username).ifPresent(user -> {
            salaRepository.findByModerador(user).stream()
                .filter(s -> !s.isAtiva())
                .filter(s -> taskRepository.existsBySalaAndEstimadaFalse(s))
                .forEach(s -> {
                    s.setAtiva(true);
                    salaRepository.save(s);
                });
        });
        return userRepository.findByUsuario(username)
                .map(salaRepository::findByModeradorOrderByIdDesc)
                .orElse(List.of());
    }

    @Transactional
    public void reativarSalasComTarefasAbertas(String username) {
        userRepository.findByUsuario(username).ifPresent(user ->
            salaRepository.findByModerador(user).stream()
                .filter(s -> !s.isAtiva())
                .filter(s -> taskRepository.existsBySalaAndEstimadaFalse(s))
                .forEach(s -> {
                    s.setAtiva(true);
                    salaRepository.save(s);
                })
        );
    }

    @Transactional
    public List<Long> inativarSalasDeModerador(String username) {
        List<Long> inativadas = new ArrayList<>();
        userRepository.findByUsuario(username).ifPresent(user ->
            salaRepository.findByModerador(user).stream()
                .filter(Sala::isAtiva)
                .forEach(sala -> {
                    sala.setAtiva(false);
                    salaRepository.save(sala);
                    inativadas.add(sala.getId());
                })
        );
        return inativadas;
    }

    public SalaDTO toDTO(Sala sala) {
        List<String> membros = salaMembroRepository.findBySala(sala)
                .stream().map(m -> m.getUsuario().getUsuario()).toList();
        return SalaDTO.from(sala, membros);
    }

    private void adicionarParticipante(Long taskId, User usuario) {
        if (!participantRepository.existsByTaskIdAndUsuario(taskId, usuario)) {
            participantRepository.save(new TaskParticipant(taskId, usuario));
        }
    }
}
