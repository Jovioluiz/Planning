package com.planningapp.service;

import com.planningapp.model.ConfiguracaoRedmine;
import com.planningapp.persistence.ConfiguracaoRedmineRepository;
import com.planningapp.security.CifraSegredos;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.DependsOn;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Logger;

/**
 * Cifra no deploy as chaves da API do Redmine gravadas em texto puro, antes da cifragem existir. O Liquibase não
 * faz isso porque não tem o segredo ({@link CifraSegredos}). Depois da primeira subida não sobra nenhuma, e a
 * consulta não acha nada.
 *
 * <p>Como o {@link CriacaoSuperUsuario}: {@code @Singleton @Startup}, com o {@code @PostConstruct} numa transação,
 * e depois das migrations (a coluna precisa já ter o tamanho do valor cifrado).</p>
 */
@Singleton
@DependsOn("MigracaoBanco")
@Startup
public class CifragemChavesRedmine {

    private static final Logger LOG = Logger.getLogger(CifragemChavesRedmine.class.getName());

    @Inject
    private ConfiguracaoRedmineRepository configuracaoRepository;

    @Inject
    private CifraSegredos cifra;

    @PostConstruct
    void cifrarChavesEmTextoPuro() {
        List<ConfiguracaoRedmine> pendentes = configuracaoRepository.listarComChaveEmTextoPuro();
        if (pendentes.isEmpty()) {
            return;
        }
        pendentes.forEach(c -> c.regravarChaveApi(cifra.cifrar(c.getChaveApi())));
        LOG.info(() -> pendentes.size() + " chave(s) da API do Redmine gravada(s) em texto puro foram cifradas");
    }
}
