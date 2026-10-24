package com.planningapp.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inativa a sala quando o moderador sai e não volta, como no Planning original (que esperava 15 s para tolerar
 * um refresh de página). Quem decide na hora é {@link SalaService#inativarSeModeradorAusente}: se o moderador
 * reconectou nesse meio tempo, em qualquer nó do cluster (a presença é distribuída), nada acontece.
 *
 * <p>{@code SALA_INATIVACAO_SEGUNDOS} muda a espera; {@code 0} desliga a inativação automática.</p>
 */
@ApplicationScoped
public class InativacaoAutomatica {

    private static final Logger LOG = Logger.getLogger(InativacaoAutomatica.class.getName());

    /** O agendador gerenciado pelo container (Jakarta Concurrency), e não um Executor próprio. */
    @Resource
    private ManagedScheduledExecutorService agendador;

    @Inject
    private SalaService salaService;

    private long segundos;

    @PostConstruct
    void configurar() {
        String valor = System.getenv("SALA_INATIVACAO_SEGUNDOS");
        try {
            segundos = valor == null || valor.isBlank() ? 15 : Long.parseLong(valor.trim());
        } catch (NumberFormatException e) {
            LOG.warning("SALA_INATIVACAO_SEGUNDOS inválido (" + valor + "); usando 15");
            segundos = 15;
        }
    }

    public void agendar(Long salaId, String moderador) {
        if (segundos <= 0) {
            return;
        }
        agendador.schedule(() -> {
            try {
                salaService.inativarSeModeradorAusente(salaId, moderador);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Falha ao inativar a sala " + salaId + " após a saída do moderador", e);
            }
        }, segundos, TimeUnit.SECONDS);
    }
}
