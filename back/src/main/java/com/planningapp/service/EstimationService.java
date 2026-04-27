package com.planningapp.service;

import com.planningapp.entity.Estimation;
import com.planningapp.entity.TaskParticipant;
import com.planningapp.entity.enums.TipoPerfil;
import com.planningapp.repository.EstimationRepository;
import com.planningapp.repository.TaskParticipantRepository;
import com.planningapp.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EstimationService {

    @Autowired
    private EstimationRepository estimationRepository;

    @Autowired
    private TaskParticipantRepository participantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessaoService sessaoService;

    public List<Estimation> findAll() {
        return estimationRepository.findAll();
    }

    public List<Estimation> findByTaskId(Long taskId) {
        return estimationRepository.findByTaskId(taskId);
    }

    public List<Estimation> findByTaskIdAndRodada(Long taskId, Integer rodada) {
        return estimationRepository.findByTaskIdAndRodada(taskId, rodada);
    }

    @Transactional
    public Estimation save(Estimation estimation) {
        return estimationRepository.save(estimation);
    }

    @Transactional
    public void saveAll(List<Estimation> estimations) {
        estimationRepository.saveAll(estimations);
    }

    @Transactional
    public void delete(Long id) {
        estimationRepository.deleteById(id);
    }

    @Transactional
    public void deleteAll(List<Estimation> estimations) {
        estimationRepository.deleteAll(estimations);
    }

    public Optional<Estimation> findByTaskIdAndParticipante(Long taskId, String username) {
        return userRepository.findByUsuario(username)
                .flatMap(user -> estimationRepository.findByTaskIdAndUsuario(taskId, user));
    }

    public Optional<Estimation> findByTaskIdAndParticipanteAndRodada(Long taskId, String username, Integer rodada) {
        return userRepository.findByUsuario(username)
                .flatMap(user -> estimationRepository.findByTaskIdAndUsuarioAndRodada(taskId, user, rodada));
    }

    public boolean todosVotaramPontos(Long taskId, Integer rodada) {
        List<String> participantesOnline = participantesOnlineDaTarefa(taskId);
        if (participantesOnline.isEmpty()) return false;
        List<Estimation> estimativas = estimationRepository.findByTaskIdAndRodada(taskId, rodada);
        long votaram = estimativas.stream()
                .filter(e -> e.getPontos() != null && participantesOnline.contains(e.getUsuario().getUsuario()))
                .count();
        return votaram >= participantesOnline.size();
    }

    public boolean todosVotaramHoras(Long taskId, Integer rodada) {
        List<String> participantesOnline = participantesOnlineDaTarefa(taskId);
        if (participantesOnline.isEmpty()) return false;
        List<Estimation> estimativas = estimationRepository.findByTaskIdAndRodada(taskId, rodada);
        long votaram = estimativas.stream()
                .filter(e -> e.getHoras() != null && e.getHoras() > 0
                        && participantesOnline.contains(e.getUsuario().getUsuario()))
                .count();
        return votaram >= participantesOnline.size();
    }

    public List<String> participantesOnlineDaTarefa(Long taskId) {
        List<String> online = sessaoService.getUsuariosOnline();
        List<TaskParticipant> participantes = participantRepository.findByTaskId(taskId);

        // Apenas JOGADORs são esperados para votar
        List<String> jogadores = participantes.stream()
                .filter(p -> p.getUsuario().getTipoPerfil() == TipoPerfil.JOGADOR)
                .map(p -> p.getUsuario().getUsuario())
                .collect(Collectors.toList());

        if (online.isEmpty()) {
            // Sessões não rastreadas (ex: após restart) → usa todos os participantes
            return jogadores;
        }
        return jogadores.stream().filter(online::contains).collect(Collectors.toList());
    }
}
