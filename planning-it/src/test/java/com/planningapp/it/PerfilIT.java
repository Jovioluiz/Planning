package com.planningapp.it;

import com.planningapp.it.Cenario.Usuario;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Meu perfil" (e-mail, foto, senha), o "Esqueci minha senha" com o e-mail de verdade (num SMTP falso) e o ambiente. */
class PerfilIT {

    private static final int PORTA_SMTP = Integer.getInteger("it.smtp.porta", 10325);
    /** Cabeçalho de um JPEG: o servidor confere o tipo pelos primeiros bytes. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0};

    private static SmtpFalso smtp;

    @BeforeAll
    static void abrirSmtp() throws Exception {
        smtp = new SmtpFalso(PORTA_SMTP);
    }

    @AfterAll
    static void fecharSmtp() throws Exception {
        smtp.close();
    }

    @Test
    void ambienteDosTestesNaoEhProducao() {
        JsonObject ambiente = Api.get("/ambiente", null).json();

        assertEquals("testes", ambiente.getString("ambiente"));
        assertFalse(ambiente.getBoolean("producao"));
    }

    @Test
    void emailEhUnicoEmMinusculas() {
        Usuario ana = Cenario.usuario("JOGADOR");
        Usuario bia = Cenario.usuario("OBSERVADOR");
        String email = Api.unico("Ana") + "@Empresa.com";

        JsonObject perfil = Api.put("/perfil/email", ana.token(), "{\"email\":\"" + email + "\"}").json();
        assertEquals(email.toLowerCase(), perfil.getString("email"));
        assertEquals(ana.nome(), Api.get("/perfil", ana.token()).json().getString("usuario"));

        assertEquals(409, Api.put("/perfil/email", bia.token(), "{\"email\":\"" + email.toLowerCase() + "\"}").status());
        assertEquals(400, Api.put("/perfil/email", bia.token(), "{\"email\":\"nao-e-email\"}").status());

        JsonObject semEmail = Api.put("/perfil/email", ana.token(), "{\"email\":\"\"}").json();
        assertTrue(!semEmail.containsKey("email") || semEmail.isNull("email"));
    }

    @Test
    void fotoEnviadaVistaPorOutroERemovida() throws Exception {
        Usuario ana = Cenario.usuario("JOGADOR");
        Usuario bia = Cenario.usuario("TESTE");

        assertEquals(204, Api.get("/fotos/" + ana.nome(), bia.token()).status(), "sem foto: 204");

        Api.Resposta enviada = Api.enviar(Api.requisicao("/perfil/foto", ana.token())
                .header("Content-Type", "image/jpeg").PUT(HttpRequest.BodyPublishers.ofByteArray(JPEG)));
        assertEquals(200, enviada.status(), enviada::toString);
        assertTrue(enviada.json().getBoolean("temFoto"));

        HttpResponse<byte[]> foto = java.net.http.HttpClient.newHttpClient().send(
                Api.requisicao("/fotos/" + ana.nome(), bia.token()).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, foto.statusCode());
        assertEquals("image/jpeg", foto.headers().firstValue("Content-Type").orElse(""));
        assertArrayEquals(JPEG, foto.body());

        assertEquals(401, Api.get("/fotos/" + ana.nome(), null).status(), "só para quem está logado");

        Api.Resposta svg = Api.enviar(Api.requisicao("/perfil/foto", ana.token())
                .header("Content-Type", "image/png").PUT(HttpRequest.BodyPublishers.ofString("<svg onload=alert(1)>")));
        assertEquals(400, svg.status(), svg::toString);

        assertFalse(Api.delete("/perfil/foto", ana.token()).json().getBoolean("temFoto"));
        assertEquals(204, Api.get("/fotos/" + ana.nome(), bia.token()).status());
    }

    @Test
    void trocaDeSenhaExigeASenhaAtual() {
        Usuario ana = Cenario.usuario("JOGADOR");

        Api.Resposta errada = Api.put("/perfil/senha", ana.token(), "{\"senhaAtual\":\"chute\",\"novaSenha\":\"nova-senha-1\"}");
        assertEquals(422, errada.status(), errada::toString);
        assertEquals(400, Api.put("/perfil/senha", ana.token(),
                "{\"senhaAtual\":\"" + Cenario.SENHA + "\",\"novaSenha\":\"curta\"}").status());

        assertEquals(204, Api.put("/perfil/senha", ana.token(),
                "{\"senhaAtual\":\"" + Cenario.SENHA + "\",\"novaSenha\":\"nova-senha-1\"}").status());

        assertNotNull(Api.login(ana.nome(), "nova-senha-1"));
        assertEquals(401, Api.post("/auth/login", null,
                "{\"usuario\":\"" + ana.nome() + "\",\"senha\":\"" + Cenario.SENHA + "\"}").status());
    }

    @Test
    void esqueciMinhaSenhaPeloLinkDoEmail() throws Exception {
        Usuario ana = Cenario.usuario("JOGADOR");
        String email = Api.unico("ana") + "@empresa.com";
        Api.put("/perfil/email", ana.token(), "{\"email\":\"" + email + "\"}");
        smtp.limpar();

        // Sem cadastro: mesma resposta, nenhum e-mail
        assertEquals(202, Api.post("/auth/esqueci-senha", null, "{\"usuarioOuEmail\":\"" + Api.unico("ninguem") + "\"}").status());
        assertNull(smtp.proximo(2));

        assertEquals(202, Api.post("/auth/esqueci-senha", null, "{\"usuarioOuEmail\":\"" + email.toUpperCase() + "\"}").status());
        SmtpFalso.Email recebido = smtp.proximo(20);
        assertNotNull(recebido, "o e-mail não chegou ao SMTP");
        assertEquals(email, recebido.para());
        String token = token(recebido.conteudo());

        // Pedir de novo logo em seguida não manda outro e-mail (e o primeiro link continua valendo)
        assertEquals(202, Api.post("/auth/esqueci-senha", null, "{\"usuarioOuEmail\":\"" + ana.nome() + "\"}").status());
        assertNull(smtp.proximo(2));

        assertEquals(422, Api.post("/auth/redefinir-senha", null,
                "{\"token\":\"" + token + "x\",\"novaSenha\":\"pelo-link-1\"}").status());
        Api.Resposta redefinida = Api.post("/auth/redefinir-senha", null,
                "{\"token\":\"" + token + "\",\"novaSenha\":\"pelo-link-1\"}");
        assertEquals(200, redefinida.status(), redefinida::toString);
        assertEquals(ana.nome(), redefinida.json().getString("usuario"));

        assertNotNull(Api.login(ana.nome(), "pelo-link-1"));
        assertEquals(422, Api.post("/auth/redefinir-senha", null,
                "{\"token\":\"" + token + "\",\"novaSenha\":\"de-novo-1\"}").status(), "o link vale uma vez só");
    }

    /** O link vem em quoted-printable (o texto tem acentos): junta as quebras e tira o "=3D". */
    private static String token(String conteudo) {
        String decodificado = conteudo.replace("=\r\n", "").replace("=3D", "=");
        Matcher m = Pattern.compile("http://localhost:4200/#/redefinir-senha\\?token=([A-Za-z0-9_-]{43})").matcher(decodificado);
        assertTrue(m.find(), () -> "link não encontrado no e-mail:\n" + conteudo);
        return m.group(1);
    }

}
