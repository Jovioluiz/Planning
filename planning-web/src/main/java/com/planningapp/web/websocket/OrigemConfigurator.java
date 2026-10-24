package com.planningapp.web.websocket;

import com.planningapp.web.config.OrigensPermitidas;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Recusa o handshake vindo de um site que não é o nosso front. O navegador sempre envia {@code Origin} no
 * WebSocket e ele não está sujeito a CORS, então sem essa checagem qualquer página poderia abrir uma conexão
 * em nome do usuário (Cross-Site WebSocket Hijacking). Clientes fora do navegador não enviam {@code Origin}
 * e são aceitos: eles ainda precisam de um token válido.
 */
public class OrigemConfigurator extends ServerEndpointConfig.Configurator {

    @Override
    public boolean checkOrigin(String origem) {
        return origem == null || origem.isBlank() || OrigensPermitidas.permitida(origem);
    }
}
