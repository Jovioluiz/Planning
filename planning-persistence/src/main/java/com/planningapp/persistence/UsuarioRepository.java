package com.planningapp.persistence;

import com.planningapp.model.Perfil;
import com.planningapp.model.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UsuarioRepository {

    @PersistenceContext(unitName = "planningPU")
    private EntityManager em;

    public Usuario salvar(Usuario usuario) {
        if (usuario.getId() == null) {
            em.persist(usuario);
            return usuario;
        }
        return em.merge(usuario);
    }

    public Optional<Usuario> buscarPorUsuario(String usuario) {
        return em.createQuery("SELECT u FROM Usuario u WHERE u.usuario = :usuario", Usuario.class)
                .setParameter("usuario", usuario)
                .getResultStream()
                .findFirst();
    }

    public boolean existePorUsuario(String usuario) {
        return em.createQuery("SELECT COUNT(u) FROM Usuario u WHERE u.usuario = :usuario", Long.class)
                .setParameter("usuario", usuario)
                .getSingleResult() > 0;
    }

    public boolean existePorPerfil(Perfil perfil) {
        return em.createQuery("SELECT COUNT(u) FROM Usuario u WHERE u.perfil = :perfil", Long.class)
                .setParameter("perfil", perfil)
                .getSingleResult() > 0;
    }

    /** Todos, exceto os de um perfil (a gestão não mostra os SUPER, como no original). */
    public List<Usuario> listarExceto(Perfil perfil) {
        return em.createQuery("SELECT u FROM Usuario u WHERE u.perfil <> :perfil ORDER BY u.usuario", Usuario.class)
                .setParameter("perfil", perfil)
                .getResultList();
    }

    public void remover(Usuario usuario) {
        em.remove(usuario);
    }
}
