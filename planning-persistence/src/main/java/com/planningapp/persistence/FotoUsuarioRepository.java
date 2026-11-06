package com.planningapp.persistence;

import com.planningapp.model.FotoUsuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

@ApplicationScoped
public class FotoUsuarioRepository {

    @PersistenceContext(unitName = "planningPU")
    private EntityManager em;

    public Optional<FotoUsuario> buscar(Long usuarioId) {
        return Optional.ofNullable(em.find(FotoUsuario.class, usuarioId));
    }

    public boolean existe(Long usuarioId) {
        return em.createQuery("SELECT COUNT(f) FROM FotoUsuario f WHERE f.usuarioId = :id", Long.class)
                .setParameter("id", usuarioId)
                .getSingleResult() > 0;
    }

    public FotoUsuario salvar(FotoUsuario foto) {
        if (em.contains(foto)) {
            return foto;
        }
        em.persist(foto);
        return foto;
    }

    public void removerDoUsuario(Long usuarioId) {
        em.createQuery("DELETE FROM FotoUsuario f WHERE f.usuarioId = :id")
                .setParameter("id", usuarioId)
                .executeUpdate();
    }
}
