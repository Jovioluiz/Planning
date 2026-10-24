package com.planningapp.persistence;

import com.planningapp.model.MembroSala;
import com.planningapp.model.Perfil;
import com.planningapp.model.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

@ApplicationScoped
public class MembroSalaRepository {

    @PersistenceContext(unitName = "planningPU")
    private EntityManager em;

    public void salvar(MembroSala membro) {
        em.persist(membro);
    }

    public boolean existe(Long salaId, Long usuarioId) {
        return em.createQuery("""
                        SELECT COUNT(m) FROM MembroSala m
                        WHERE m.sala.id = :salaId AND m.usuario.id = :usuarioId""", Long.class)
                .setParameter("salaId", salaId)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult() > 0;
    }

    public List<String> listarNomes(Long salaId) {
        return em.createQuery("""
                        SELECT m.usuario.usuario FROM MembroSala m
                        WHERE m.sala.id = :salaId ORDER BY m.usuario.usuario""", String.class)
                .setParameter("salaId", salaId)
                .getResultList();
    }

    public List<Usuario> listarJogadores(Long salaId) {
        return em.createQuery("""
                        SELECT m.usuario FROM MembroSala m
                        WHERE m.sala.id = :salaId AND m.usuario.perfil = :perfil""", Usuario.class)
                .setParameter("salaId", salaId)
                .setParameter("perfil", Perfil.JOGADOR)
                .getResultList();
    }

    /** Antes de excluir o usuário (FK id_usuario). */
    public void removerDoUsuario(Long usuarioId) {
        em.createQuery("DELETE FROM MembroSala x WHERE x.usuario.id = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
