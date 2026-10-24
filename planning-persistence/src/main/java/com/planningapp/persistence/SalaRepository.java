package com.planningapp.persistence;

import com.planningapp.model.Sala;
import com.planningapp.model.StatusTarefa;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

/**
 * Acesso a dados de {@link Sala}. Não abre transação: quem chama (o EJB do service) é que define o escopo transacional.
 */
@ApplicationScoped
public class SalaRepository {

    @PersistenceContext(unitName = "planningPU")
    private EntityManager em;

    public Sala salvar(Sala sala) {
        if (sala.getId() == null) {
            em.persist(sala);
            return sala;
        }
        return em.merge(sala);
    }

    public Optional<Sala> buscarPorId(Long id) {
        return Optional.ofNullable(em.find(Sala.class, id));
    }

    // JOIN FETCH: estas consultas rodam sem transação (SUPPORTS), então o moderador LAZY
    // não poderia ser carregado depois que a entidade é desanexada.
    public Optional<Sala> buscarPorCodigo(String codigo) {
        return em.createQuery("SELECT s FROM Sala s JOIN FETCH s.moderador WHERE s.codigo = :codigo", Sala.class)
                .setParameter("codigo", codigo)
                .getResultStream()
                .findFirst();
    }

    /** Ativas e inativas, da mais nova para a mais antiga. */
    public List<Sala> listarDoModerador(String moderador) {
        return em.createQuery("SELECT s FROM Sala s WHERE s.moderador.usuario = :moderador ORDER BY s.criadaEm DESC", Sala.class)
                .setParameter("moderador", moderador)
                .getResultList();
    }

    /** Tarefas na fila ou em votação (as "não estimadas" do original, que contava as puladas como estimadas). */
    public long contarPendentes(Long salaId) {
        return em.createQuery("SELECT COUNT(t) FROM Tarefa t WHERE t.sala.id = :salaId AND t.status IN (:fila, :liberada)", Long.class)
                .setParameter("salaId", salaId)
                .setParameter("fila", StatusTarefa.FILA)
                .setParameter("liberada", StatusTarefa.LIBERADA)
                .getSingleResult();
    }

    public List<Sala> listarAtivas() {
        return em.createQuery("SELECT s FROM Sala s JOIN FETCH s.moderador WHERE s.ativa = true ORDER BY s.criadaEm DESC", Sala.class)
                .getResultList();
    }

    public long contarTarefas(Long salaId) {
        return em.createQuery("SELECT COUNT(t) FROM Tarefa t WHERE t.sala.id = :salaId", Long.class)
                .setParameter("salaId", salaId)
                .getSingleResult();
    }

    /** @param apenasAtivas {@code false} conta também as desativadas (que ainda guardam histórico) */
    public long contarDoModerador(Long usuarioId, boolean apenasAtivas) {
        return em.createQuery("SELECT COUNT(s) FROM Sala s WHERE s.moderador.id = :usuarioId"
                        + (apenasAtivas ? " AND s.ativa = true" : ""), Long.class)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult();
    }
}
