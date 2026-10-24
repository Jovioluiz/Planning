package com.planningapp.web.websocket;

import com.planningapp.dto.EventoSala;
import com.planningapp.security.JwtProvider;
import com.planningapp.security.TokenValidado;
import com.planningapp.service.AuthService;
import com.planningapp.service.SalaService;
import com.planningapp.service.VotacaoService;
import com.planningapp.service.presenca.PresencaSala;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Canal em tempo real de uma sala: {@code ws://host/planning/ws/salas/{salaId}?token=<jwt>}.
 *
 * <p>O token vai na query string porque a API de WebSocket do navegador não permite enviar headers
 * (o Planning original contornava isso com o frame CONNECT do STOMP). Atenção: URLs podem aparecer em logs
 * de acesso e de proxy. Se isso for um problema, troque por um "ticket" de uso único emitido por um endpoint REST.</p>
 *
 * <p>O servidor só envia mensagens. O cliente pode mandar {@code ping} para manter a conexão viva.</p>
 */
@ServerEndpoint(value = "/ws/salas/{salaId}", configurator = OrigemConfigurator.class)
public class SalaWebSocketEndpoint {

    private static final Logger LOG = Logger.getLogger(SalaWebSocketEndpoint.class.getName());

    @Inject
    private JwtProvider jwtProvider;

    @Inject
    private AuthService authService;

    @Inject
    private SalaService salaService;

    @Inject
    private SalaSessoes sessoes;

    @Inject
    private PresencaSala presenca;

    @Inject
    private VotacaoService votacaoService;

    @Inject
    private PublicadorEventosSala publicador;

    @Inject
    private Jsonb jsonb;

    @OnOpen
    public void aoAbrir(Session sessao, @PathParam("salaId") Long salaId) throws IOException {
        Optional<TokenValidado> token = Optional.ofNullable(sessao.getRequestParameterMap().get("token"))
                .flatMap(valores -> valores.stream().findFirst())
                .flatMap(jwtProvider::validar);

        if (token.isEmpty()) {
            fechar(sessao, "Token ausente, inválido ou expirado");
            return;
        }
        // Mesma regra do REST: os papéis vêm do banco, e usuário desativado não entra
        Optional<Set<String>> papeis = authService.papeisAtuais(token.get().usuario());
        if (papeis.isEmpty()) {
            fechar(sessao, "Usuário desativado ou inexistente");
            return;
        }
        String usuario = token.get().usuario();
        // Sala inativa: só o moderador entra, e ela volta a ficar ativa (ver SalaService.aceitaConexao)
        if (!salaService.aceitaConexao(salaId, usuario)) {
            fechar(sessao, "Sala não encontrada ou inativa");
            return;
        }

        sessao.getUserProperties().put(SalaSessoes.PROP_USUARIO, usuario);
        sessao.getUserProperties().put(SalaSessoes.PROP_SALA, salaId);
        sessoes.adicionar(salaId, sessao);
        presenca.entrou(salaId, usuario, papeis.get());
        avisarPresenca(salaId, usuario);
        LOG.info(() -> usuario + " conectou na sala " + salaId + " (WebSocket)");
    }

    @OnMessage
    public void aoReceber(Session sessao, String mensagem) {
        if ("ping".equalsIgnoreCase(mensagem.trim())) {
            sessoes.enviar(sessao, "pong");
        }
    }

    @OnClose
    public void aoFechar(Session sessao) {
        Long salaId = (Long) sessao.getUserProperties().get(SalaSessoes.PROP_SALA);
        if (salaId == null) {
            return; // conexão recusada no aoAbrir, nunca entrou na sala
        }
        String usuario = (String) sessao.getUserProperties().get(SalaSessoes.PROP_USUARIO);
        sessoes.remover(salaId, sessao);
        LOG.info(() -> usuario + " desconectou da sala " + salaId + " (WebSocket)");
        if (presenca.saiu(salaId, usuario)) {
            avisarPresenca(salaId, usuario);
            try {
                // Se ele era o único que faltava votar, a fase fica completa agora
                votacaoService.aoSairDaSala(salaId, usuario);
                // Se era o moderador, a sala é inativada se ele não voltar logo (como no original)
                salaService.aoSairDaSala(salaId, usuario);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Falha ao tratar a saída de " + usuario + " da sala " + salaId, e);
            }
        }
    }

    @OnError
    public void aoErrar(Session sessao, Throwable erro) {
        Object usuario = sessao.getUserProperties().get(SalaSessoes.PROP_USUARIO);
        if (erro instanceof IOException) {
            // Conexão que caiu (rede, aba fechada): o front reconecta sozinho
            LOG.info(() -> "WebSocket de " + usuario + " caiu: " + erro.getMessage());
        } else {
            LOG.log(Level.WARNING, "Erro no WebSocket de " + usuario + " (sessão " + sessao.getId() + ")", erro);
        }
    }

    /** Pelo tópico JMS, como os demais eventos: a lista é do cluster inteiro, e todos os nós precisam avisar. */
    private void avisarPresenca(Long salaId, String usuario) {
        publicador.publicar(salaId, jsonb.toJson(EventoSala.presenca(salaId, usuario, presenca.online(salaId))));
    }

    /** 1008 (VIOLATED_POLICY) é o código padrão para "conexão recusada por regra de acesso". */
    private static void fechar(Session sessao, String motivo) throws IOException {
        LOG.warning(() -> "WebSocket recusado em " + sessao.getRequestURI().getPath() + ": " + motivo);
        sessao.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, motivo));
    }
}
