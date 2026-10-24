package com.planningapp.persistence;

import com.planningapp.model.Participante;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ParticipanteRepository {

    @PersistenceContext(unitName = "planningPU")
    private EntityManager em;

    public void salvar(Participante participante) {
        em.persist(participante);
    }

    public Optional<Participante> buscar(Long tarefaId, Long usuarioId) {
        return em.createQuery("""
                        SELECT p FROM Participante p
                        WHERE p.tarefa.id = :tarefaId AND p.usuario.id = :usuarioId""", Participante.class)
                .setParameter("tarefaId", tarefaId)
                .setParameter("usuarioId", usuarioId)
                .getResultStream()
                .findFirst();
    }

    /** Todos, inclusive os removidos, com o usuário carregado. */
    public List<Participante> listarDaTarefa(Long tarefaId) {
        return em.createQuery("""
                        SELECT p FROM Participante p JOIN FETCH p.usuario
                        WHERE p.tarefa.id = :tarefaId
                        ORDER BY p.usuario.usuario""", Participante.class)
                .setParameter("tarefaId", tarefaId)
                .getResultList();
    }

    /** Nomes dos participantes que ainda podem votar. */
    public Set<String> ativos(Long tarefaId) {
        return em.createQuery("""
                        SELECT p.usuario.usuario FROM Participante p
                        WHERE p.tarefa.id = :tarefaId AND p.removidoEm IS NULL""", String.class)
                .setParameter("tarefaId", tarefaId)
                .getResultStream()
                .collect(Collectors.toSet());
    }

    /** Antes de excluir a tarefa (FK participantes_tarefa.id_tarefa). */
    public void removerDaTarefa(Long tarefaId) {
        em.createQuery("DELETE FROM Participante p WHERE p.tarefa.id = :tarefaId")
                .setParameter("tarefaId", tarefaId)
                .executeUpdate();
    }

    /** Antes de excluir o usuário (FK id_usuario). */
    public void removerDoUsuario(Long usuarioId) {
        em.createQuery("DELETE FROM Participante x WHERE x.usuario.id = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
