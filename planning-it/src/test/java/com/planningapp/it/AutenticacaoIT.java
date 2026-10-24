package com.planningapp.it;

import com.planningapp.it.Cenario.Usuario;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JWT, Jakarta Security (Elytron com integrated-jaspi=false) e CORS, como o servidor de verdade se comporta. */
class AutenticacaoIT {

    private static final String FRONT = "http://localhost:4200";

    @Test
    void semTokenDa401ComDesafioBearerEHeadersDeCors() {
        Api.Resposta resposta = Api.enviar(Api.requisicao("/salas", null).header("Origin", FRONT).GET());

        assertEquals(401, resposta.status());
        assertEquals("Bearer", resposta.header("WWW-Authenticate"));
        // Sem CORS no 401, o navegador mostraria "erro de CORS" em vez do 401 (ver JwtAuthenticationMechanism)
        assertEquals(FRONT, resposta.header("Access-Control-Allow-Origin"));
    }

    @Test
    void preflightDoNavegadorEhAceitoParaOFront() {
        Api.Resposta resposta = Api.enviar(Api.requisicao("/salas", null)
                .header("Origin", FRONT)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));

        assertTrue(resposta.status() < 300, resposta::toString);
        assertEquals(FRONT, resposta.header("Access-Control-Allow-Origin"));
    }

    @Test
    void cadastroLoginEMe() {
        Usuario ana = Cenario.usuario("JOGADOR");

        JsonObject me = Api.get("/auth/me", ana.token()).json();

        assertEquals(ana.nome(), me.getString("usuario"));
        assertEquals("JOGADOR", me.getString("perfil"));
    }

    @Test
    void senhaErradaETokenAdulteradoDao401() {
        Usuario ana = Cenario.usuario("JOGADOR");

        Api.Resposta senhaErrada = Api.post("/auth/login", null,
                "{\"usuario\":\"" + ana.nome() + "\",\"senha\":\"outra-senha\"}");
        assertEquals(401, senhaErrada.status());
        assertEquals("Usuário ou senha inválidos", senhaErrada.mensagem());

        String adulterado = ana.token().substring(0, ana.token().length() - 2) + "xx";
        assertEquals(401, Api.get("/auth/me", adulterado).status());
    }

    @Test
    void perfilSemPermissaoDa403() {
        Usuario ana = Cenario.usuario("JOGADOR");

        assertEquals(403, Api.post("/salas", ana.token(), "{\"nome\":\"Sala da ana\"}").status());
    }

    @Test
    void superEhCriadoNoDeployEmasNuncaPelaApi() {
        JsonObject me = Api.get("/auth/me", Cenario.tokenDoSuper()).json();
        assertEquals("SUPER", me.getString("perfil"));

        Api.Resposta resposta = Api.post("/auth/registrar", null,
                "{\"usuario\":\"" + Api.unico("super") + "\",\"senha\":\"segredo1\",\"perfil\":\"SUPER\"}");
        assertEquals(422, resposta.status());
    }
}
