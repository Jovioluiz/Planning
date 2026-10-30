package com.planningapp.persistence;

import com.planningapp.model.ConfiguracaoRedmine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ConfiguracaoRedmineRepository {

    @PersistenceContext(unitName = "planningPU")
    private EntityManager em;

    public void salvar(ConfiguracaoRedmine configuracao) {
        em.persist(configuracao);
    }

    /** As que ainda têm a chave da API em texto puro (gravadas antes da cifragem, sem o prefixo "v1:"). */
    public List<ConfiguracaoRedmine> listarComChaveEmTextoPuro() {
        return em.createQuery("SELECT c FROM ConfiguracaoRedmine c WHERE c.chaveApi NOT LIKE 'v1:%'",
                        ConfiguracaoRedmine.class)
                .getResultList();
    }

    public Optional<ConfiguracaoRedmine> buscarDoUsuario(Long usuarioId) {
        return em.createQuery("SELECT c FROM ConfiguracaoRedmine c WHERE c.usuario.id = :usuarioId",
                        ConfiguracaoRedmine.class)
                .setParameter("usuarioId", usuarioId)
                .getResultStream()
                .findFirst();
    }

    /** Antes de excluir o usuário (FK id_usuario). */
    public void removerDoUsuario(Long usuarioId) {
        em.createQuery("DELETE FROM ConfiguracaoRedmine c WHERE c.usuario.id = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
