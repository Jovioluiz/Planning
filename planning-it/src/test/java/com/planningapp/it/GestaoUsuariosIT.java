package com.planningapp.it;

import com.planningapp.it.Cenario.Sala;
import com.planningapp.it.Cenario.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Gestão de usuários pelo SUPER, com efeito imediato sobre tokens já emitidos. */
class GestaoUsuariosIT {

    private final String superToken = Cenario.tokenDoSuper();

    @Test
    void desativarDerrubaOTokenJaEmitidoEReativarDevolveOAcesso() {
        Usuario ana = Cenario.usuario("JOGADOR");
        assertEquals(200, Api.get("/auth/me", ana.token()).status());

        assertEquals(200, Api.post("/usuarios/" + ana.nome() + "/desativar", superToken, null).status());
        assertEquals(401, Api.get("/auth/me", ana.token()).status(), "o token antigo para de valer na hora");
        Api.Resposta login = Api.post("/auth/login", null, "{\"usuario\":\"" + ana.nome() + "\",\"senha\":\"" + Cenario.SENHA + "\"}");
        assertEquals(401, login.status());
        assertEquals("Usuário desativado. Procure o administrador", login.mensagem());

        Api.post("/usuarios/" + ana.nome() + "/reativar", superToken, null);
        assertEquals(200, Api.get("/auth/me", ana.token()).status());
    }

    @Test
    void mudarOPerfilValeParaOTokenQueJaExiste() {
        Usuario bia = Cenario.usuario("JOGADOR");
        Usuario admin = Cenario.usuario("ADMIN");
        Sala sala = Cenario.sala(admin);

        Api.put("/usuarios/" + bia.nome() + "/perfil", superToken, "{\"perfil\":\"ADMIN\"}");

        assertEquals(201, Api.post("/salas", bia.token(), "{\"nome\":\"Sala da bia\"}").status(), "token emitido quando era JOGADOR");
        assertEquals(409, Api.put("/usuarios/" + admin.nome() + "/perfil", superToken, "{\"perfil\":\"JOGADOR\"}").status(),
                "modera a sala " + sala.id() + ", ativa");
    }

    @Test
    void excluirSoQuemNaoTemHistorico() {
        Usuario admin = Cenario.usuario("ADMIN");
        Cenario.sala(admin);
        Usuario novo = Cenario.usuario("OBSERVADOR");

        assertEquals(409, Api.delete("/usuarios/" + admin.nome(), superToken).status());
        assertEquals(204, Api.delete("/usuarios/" + novo.nome(), superToken).status());
        assertEquals(401, Api.get("/auth/me", novo.token()).status());
    }

    @Test
    void soOSuperGerenciaENinguemMexeNoSuper() {
        Usuario admin = Cenario.usuario("ADMIN");

        assertEquals(403, Api.get("/usuarios", admin.token()).status());
        assertEquals(403, Api.post("/usuarios/" + Api.SUPER_USUARIO + "/desativar", superToken, null).status());
    }
}
