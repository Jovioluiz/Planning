package com.planningapp.it;

import jakarta.json.JsonObject;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Monta o ponto de partida dos testes. Como o cadastro público só aceita o primeiro ADMIN, cada teste cria o
 * seu ADMIN pelo SUPER (que o servidor cria no deploy a partir de SUPER_USUARIO/SUPER_SENHA). Assim os testes
 * não dependem uns dos outros nem da ordem em que rodam.
 */
public final class Cenario {

    public static final String SENHA = "senha-dos-testes";

    private Cenario() {
    }

    public static String tokenDoSuper() {
        return Api.login(Api.SUPER_USUARIO, Api.SUPER_SENHA);
    }

    /** Cria o usuário (pelo cadastro público, ou pelo SUPER quando é ADMIN) e devolve o token. */
    public static Usuario usuario(String perfil) {
        String nome = Api.unico(perfil.toLowerCase());
        String corpo = "{\"usuario\":\"" + nome + "\",\"senha\":\"" + SENHA + "\",\"perfil\":\"" + perfil + "\"}";
        Api.Resposta resposta = "ADMIN".equals(perfil)
                ? Api.post("/usuarios", tokenDoSuper(), corpo)
                : Api.post("/auth/registrar", null, corpo);
        assertEquals(201, resposta.status(), () -> "Criar " + nome + ": " + resposta);
        return new Usuario(nome, Api.login(nome, SENHA));
    }

    public record Usuario(String nome, String token) {
    }

    public record Sala(long id, String codigo) {
    }

    public static Sala sala(Usuario moderador) {
        Api.Resposta resposta = Api.post("/salas", moderador.token(), "{\"nome\":\"" + Api.unico("Sala") + "\"}");
        assertEquals(201, resposta.status(), resposta::toString);
        JsonObject sala = resposta.json();
        return new Sala(sala.getJsonNumber("id").longValue(), sala.getString("codigo"));
    }

    public static void entrar(Sala sala, Usuario... usuarios) {
        for (Usuario usuario : usuarios) {
            Api.Resposta resposta = Api.post("/salas/codigo/" + sala.codigo() + "/entrar", usuario.token(), null);
            assertEquals(200, resposta.status(), resposta::toString);
        }
    }

    public static long tarefa(Sala sala, Usuario moderador, String numero) {
        Api.Resposta resposta = Api.post("/salas/" + sala.id() + "/tarefas", moderador.token(),
                "{\"numero\":\"" + numero + "\",\"titulo\":\"Tarefa " + numero + "\"}");
        assertEquals(201, resposta.status(), resposta::toString);
        return resposta.json().getJsonNumber("id").longValue();
    }

    /** Caminho base da votação de uma tarefa. */
    public static String votacao(Sala sala, long tarefaId) {
        return "/salas/" + sala.id() + "/tarefas/" + tarefaId + "/votacao";
    }
}
