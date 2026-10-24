package com.planningapp.persistence;

import com.planningapp.model.Estimativa;
import com.planningapp.model.StatusTarefa;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class EstimativaRepository {

    @PersistenceContext(unitName = "planningPU")
    private EntityManager em;

    public Estimativa salvar(Estimativa estimativa) {
        if (estimativa.getId() == null) {
            em.persist(estimativa);
            return estimativa;
        }
        return em.merge(estimativa);
    }

    public Optional<Estimativa> buscar(Long tarefaId, Long usuarioId, int rodada) {
        return em.createQuery("""
                        SELECT e FROM Estimativa e
                        WHERE e.tarefa.id = :tarefaId AND e.usuario.id = :usuarioId AND e.rodada = :rodada""",
                        Estimativa.class)
                .setParameter("tarefaId", tarefaId)
                .setParameter("usuarioId", usuarioId)
                .setParameter("rodada", rodada)
                .getResultStream()
                .findFirst();
    }

    /** Votos de uma rodada, já com o usuário carregado (o DTO precisa do nome). */
    public List<Estimativa> listarDaRodada(Long tarefaId, int rodada) {
        return em.createQuery("""
                        SELECT e FROM Estimativa e JOIN FETCH e.usuario
                        WHERE e.tarefa.id = :tarefaId AND e.rodada = :rodada
                        ORDER BY e.usuario.usuario""", Estimativa.class)
                .setParameter("tarefaId", tarefaId)
                .setParameter("rodada", rodada)
                .getResultList();
    }

    /**
     * Votos da rodada final ({@code rodada = rodadaAtual}) das tarefas finalizadas, numa consulta só.
     * As rodadas anteriores não entram nas estatísticas, como no original.
     *
     * @param moderador só as salas deste moderador; {@code null} para todas
     */
    public List<Estimativa> listarDaRodadaFinalDasEstimadas(String moderador) {
        String jpql = """
                SELECT e FROM Estimativa e JOIN FETCH e.usuario JOIN e.tarefa t
                WHERE t.status = :estimada AND e.rodada = t.rodadaAtual""" + (moderador != null ? " AND t.sala.moderador.usuario = :moderador" : "");
        var query = em.createQuery(jpql, Estimativa.class).setParameter("estimada", StatusTarefa.ESTIMADA);
        if (moderador != null) {
            query.setParameter("moderador", moderador);
        }
        return query.getResultList();
    }

    public void remover(Estimativa estimativa) {
        em.remove(estimativa);
    }

    /** Remove todo o histórico de votos antes de excluir a tarefa (FK estimativas.id_tarefa). */
    public void removerDaTarefa(Long tarefaId) {
        em.createQuery("DELETE FROM Estimativa e WHERE e.tarefa.id = :tarefaId")
                .setParameter("tarefaId", tarefaId)
                .executeUpdate();
    }

    public boolean existeDoUsuario(Long usuarioId) {
        return em.createQuery("SELECT COUNT(e) FROM Estimativa e WHERE e.usuario.id = :usuarioId", Long.class)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult() > 0;
    }
}
