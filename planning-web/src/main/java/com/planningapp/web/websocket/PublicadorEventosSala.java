package com.planningapp.web.websocket;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Publica os eventos das salas no tópico JMS. Cada nó do cluster tem um {@link EntregaEventosSala} assinando o
 * tópico e entregando às suas conexões WebSocket, então um voto feito no nó A chega a quem está no nó B.
 *
 * <p>Usa a {@code java:/ConnectionFactory} (in-vm, sem JCA), e não a {@code java:/JmsXA}: a publicação acontece no
 * {@code AFTER_SUCCESS}, quando a transação JTA já terminou, e a JmsXA tentaria se alistar nela.</p>
 *
 * <p>Um único {@link JMSContext} por nó, usado com {@code synchronized}: o JMSContext não é thread-safe, e um só
 * produtor garante que os eventos saiam na ordem em que foram publicados (a especificação JMS garante a ordem por
 * sessão, não entre sessões).</p>
 */
@ApplicationScoped
public class PublicadorEventosSala {

    static final String PROPRIEDADE_SALA = "salaId";

    private static final Logger LOG = Logger.getLogger(PublicadorEventosSala.class.getName());

    @Resource(lookup = "java:/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(lookup = EntregaEventosSala.TOPICO)
    private Topic topico;

    private JMSContext contexto;

    /**
     * Uma notificação perdida não pode desfazer um voto já gravado: falha no broker vira log, e a próxima
     * publicação tenta de novo com um contexto novo. O cliente se recupera relendo a votação (GET).
     */
    public synchronized void publicar(Long salaId, String json) {
        try {
            if (contexto == null) {
                contexto = connectionFactory.createContext();
            }
            TextMessage mensagem = contexto.createTextMessage(json);
            mensagem.setLongProperty(PROPRIEDADE_SALA, salaId);
            contexto.createProducer().send(topico, mensagem);
        } catch (JMSRuntimeException | jakarta.jms.JMSException e) {
            LOG.log(Level.WARNING, "Falha ao publicar evento da sala " + salaId + " no tópico JMS", e);
            fechar();
        }
    }

    @PreDestroy
    synchronized void fechar() {
        if (contexto != null) {
            try {
                contexto.close();
            } catch (JMSRuntimeException e) {
                LOG.log(Level.FINE, "Falha ao fechar o JMSContext", e);
            }
            contexto = null;
        }
    }
}
