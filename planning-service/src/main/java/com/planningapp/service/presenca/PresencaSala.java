package com.planningapp.service.presenca;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import org.infinispan.Cache;
import org.infinispan.context.Flag;
import org.infinispan.remoting.transport.Address;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Quem está conectado em cada sala, com seus papéis, <b>no cluster inteiro</b>. Fica no service (e não no WAR)
 * porque a regra de "todos votaram" cruza quem está online com quem votou. A camada web só informa entradas e saídas.
 *
 * <p>Os dados ficam num cache do Infinispan configurado no servidor ({@code cache-container=planning},
 * cache {@code presenca}): {@code local-cache} num nó só, {@code replicated-cache} em cluster (cada nó tem a cópia
 * inteira e as escritas são síncronas). O código é o mesmo nos dois casos.</p>
 *
 * <ul>
 *   <li>Chave {@code sala|usuario|nó}, valor {@code papeis;conexoes}: cada nó só escreve as próprias chaves, então
 *       não há disputa entre nós; e só {@code String}, que o marshaller (ProtoStream) já sabe transmitir.</li>
 *   <li>Várias abas do mesmo usuário somam conexões; ele só sai quando fecha a última, em qualquer nó.</li>
 *   <li>Nó que cai sem avisar deixaria as conexões dele "online" para sempre. Por isso toda leitura ignora (e
 *       apaga) as entradas de nós que não estão mais entre os membros do cluster. Vale na hora, sem depender de
 *       notificação. (Um listener de {@code @ViewChanged} no cache manager foi a primeira tentativa, mas o cache
 *       manager que o WildFly expõe à aplicação não o notificou num teste derrubando um nó.)</li>
 * </ul>
 */
@ApplicationScoped
public class PresencaSala {

    private static final Logger LOG = Logger.getLogger(PresencaSala.class.getName());
    private static final String SEP = "|";

    @Resource(lookup = "java:jboss/infinispan/cache/planning/presenca")
    private Cache<String, String> cache;

    private String no;

    @PostConstruct
    void iniciar() {
        no = nome(cache.getCacheManager().getAddress());
        // Sobras deste nó de uma execução anterior que caiu sem limpar
        removerSe(dono -> dono.equals(no));
    }

    /** Desligamento normal: as conexões deste nó deixam de existir. */
    @PreDestroy
    void parar() {
        removerSe(dono -> dono.equals(no));
    }

    public synchronized void entrou(Long salaId, String usuario, Set<String> papeis) {
        String chave = chave(salaId, usuario, no);
        Presente atual = Presente.ler(cache.get(chave));
        cache.put(chave, new Presente(Set.copyOf(papeis), atual == null ? 1 : atual.conexoes() + 1).escrever());
    }

    /** @return {@code true} se o usuário saiu de vez: não tem mais conexão na sala em nenhum nó */
    public synchronized boolean saiu(Long salaId, String usuario) {
        String chave = chave(salaId, usuario, no);
        Presente atual = Presente.ler(cache.get(chave));
        if (atual == null) {
            return false;
        }
        if (atual.conexoes() > 1) {
            cache.put(chave, new Presente(atual.papeis(), atual.conexoes() - 1).escrever());
            return false;
        }
        cache.remove(chave);
        return entradasDaSala(salaId).keySet().stream().noneMatch(c -> usuarioDe(c).equals(usuario));
    }

    public List<String> online(Long salaId) {
        return entradasDaSala(salaId).keySet().stream().map(PresencaSala::usuarioDe).distinct().sorted().toList();
    }

    public Set<String> onlineComPapel(Long salaId, String papel) {
        return entradasDaSala(salaId).entrySet().stream()
                .filter(e -> Presente.ler(e.getValue()).papeis().contains(papel))
                .map(e -> usuarioDe(e.getKey()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Entradas da sala dos nós que ainda estão no cluster. Leitura local: num cache replicado cada nó já tem todos
     * os dados. Copia para um mapa comum, para não depender de stream distribuído do Infinispan.
     */
    private Map<String, String> entradasDaSala(Long salaId) {
        Set<String> nosAtivos = nosAtivos();
        String prefixo = salaId + SEP;
        Map<String, String> daSala = new HashMap<>();
        List<String> deNosQueSairam = new ArrayList<>();
        for (Map.Entry<String, String> e : cache.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL).entrySet()) {
            if (!e.getKey().startsWith(prefixo)) {
                continue;
            }
            if (nosAtivos.contains(noDe(e.getKey()))) {
                daSala.put(e.getKey(), e.getValue());
            } else {
                deNosQueSairam.add(e.getKey());
            }
        }
        if (!deNosQueSairam.isEmpty()) {
            deNosQueSairam.forEach(cache::remove);
            LOG.info("Presença: " + deNosQueSairam.size() + " conexão(ões) de nós que saíram do cluster foram removidas");
        }
        return daSala;
    }

    /** Membros atuais do cluster; fora de cluster, só este nó. */
    private Set<String> nosAtivos() {
        List<Address> membros = cache.getCacheManager().getMembers();
        return membros == null ? Set.of(no) : membros.stream().map(PresencaSala::nome).collect(Collectors.toSet());
    }

    private void removerSe(Predicate<String> doNo) {
        List<String> remover = new ArrayList<>();
        for (String chave : cache.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL).keySet()) {
            if (doNo.test(noDe(chave))) {
                remover.add(chave);
            }
        }
        remover.forEach(cache::remove);
    }

    private static String nome(Address endereco) {
        return endereco != null ? endereco.toString() : "local";
    }

    private static String chave(Long salaId, String usuario, String no) {
        return salaId + SEP + usuario + SEP + no;
    }

    private static String usuarioDe(String chave) {
        return chave.split("\\|")[1];
    }

    private static String noDe(String chave) {
        return chave.split("\\|")[2];
    }

    /** Valor guardado no cache, em texto: {@code JOGADOR,ADMIN;2}. */
    private record Presente(Set<String> papeis, int conexoes) {

        static Presente ler(String valor) {
            if (valor == null) {
                return null;
            }
            String[] partes = valor.split(";");
            Set<String> papeis = partes[0].isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(partes[0].split(",")));
            return new Presente(papeis, Integer.parseInt(partes[1]));
        }

        String escrever() {
            return String.join(",", papeis) + ";" + conexoes;
        }
    }
}
