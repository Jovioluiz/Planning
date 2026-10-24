package com.planningapp.persistence;

import com.planningapp.model.StatusTarefa;
import com.planningapp.model.Tarefa;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TarefaRepository {

    @PersistenceContext(unitName = "planningPU")
    private EntityManager em;

    public Tarefa salvar(Tarefa tarefa) {
        if (tarefa.getId() == null) {
            em.persist(tarefa);
            return tarefa;
        }
        return em.merge(tarefa);
    }

    public Optional<Tarefa> buscarPorId(Long id) {
        return Optional.ofNullable(em.find(Tarefa.class, id));
    }

    public List<Tarefa> listarPorSala(Long salaId) {
        return em.createQuery("SELECT t FROM Tarefa t WHERE t.sala.id = :salaId ORDER BY t.numero", Tarefa.class)
                .setParameter("salaId", salaId)
                .getResultList();
    }

    public boolean existeNumeroNaSala(Long salaId, String numero) {
        return em.createQuery("SELECT COUNT(t) FROM Tarefa t WHERE t.sala.id = :salaId AND t.numero = :numero", Long.class)
                .setParameter("salaId", salaId)
                .setParameter("numero", numero)
                .getSingleResult() > 0;
    }

    /** Para a importação checar duplicadas numa consulta só, em vez de uma por linha. */
    public Set<String> numerosDaSala(Long salaId) {
        return em.createQuery("SELECT t.numero FROM Tarefa t WHERE t.sala.id = :salaId", String.class)
                .setParameter("salaId", salaId)
                .getResultStream()
                .collect(Collectors.toSet());
    }

    /**
     * Tarefas finalizadas ou puladas, com a sala carregada.
     *
     * <p>Sem alias no {@code JOIN FETCH}: o WildFly liga o modo estrito de JPQL do Hibernate, e a especificação
     * JPA não permite alias em fetch join ("Encountered aliased fetch join, but strict JPQL compliance was requested").</p>
     *
     * @param moderador só as salas deste moderador; {@code null} para todas
     */
    public List<Tarefa> listarEncerradas(String moderador) {
        String jpql = """
                SELECT t FROM Tarefa t JOIN FETCH t.sala
                WHERE t.status IN (:estimada, :pulada)""" + (moderador != null ? " AND t.sala.moderador.usuario = :moderador" : "");
        var query = em.createQuery(jpql, Tarefa.class)
                .setParameter("estimada", StatusTarefa.ESTIMADA)
                .setParameter("pulada", StatusTarefa.PULADA);
        if (moderador != null) {
            query.setParameter("moderador", moderador);
        }
        return query.getResultList();
    }

    public void remover(Tarefa tarefa) {
        em.remove(em.contains(tarefa) ? tarefa : em.merge(tarefa));
    }
}
