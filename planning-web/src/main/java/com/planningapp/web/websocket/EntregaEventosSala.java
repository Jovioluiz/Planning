package com.planningapp.web.websocket;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Recebe os eventos do tópico e entrega às conexões WebSocket <b>deste nó</b>. Em cluster, cada nó tem a sua
 * assinatura (não durável) do tópico, então todos recebem todos os eventos; cada um entrega só às suas sessões.
 *
 * <p>{@code maxSession=1}: um MDB consome em paralelo por padrão, e aí dois eventos da mesma transação
 * (ex.: {@code VOTO_REGISTRADO} e {@code TODOS_VOTARAM}) poderiam chegar invertidos, o bug que o
 * {@code TempoRealIT} pega. Com uma sessão só, a entrega segue a ordem de publicação.</p>
 *
 * <p>O tópico é criado pela própria aplicação ({@code @JMSDestinationDefinition}), sem configuração no servidor
 * além de usar um perfil com mensageria ({@code standalone-full.xml}).</p>
 */
@JMSDestinationDefinition(
        name = EntregaEventosSala.TOPICO,
        interfaceName = "jakarta.jms.Topic",
        destinationName = "planning.eventos-sala")
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = EntregaEventosSala.TOPICO),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED) // entregar no WebSocket não é transacional
public class EntregaEventosSala implements MessageListener {

    public static final String TOPICO = "java:/jms/topic/planning/EventosSala";

    private static final Logger LOG = Logger.getLogger(EntregaEventosSala.class.getName());

    @Inject
    private SalaSessoes sessoes;

    @Override
    public void onMessage(Message mensagem) {
        try {
            long salaId = mensagem.getLongProperty(PublicadorEventosSala.PROPRIEDADE_SALA);
            sessoes.enviar(salaId, ((TextMessage) mensagem).getText());
        } catch (JMSException | ClassCastException e) {
            LOG.log(Level.WARNING, "Mensagem inválida no tópico de eventos das salas", e);
        }
    }
}
