package com.planningapp.service.email;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguracaoEmailTest {

    @Test
    void semHostOEnvioFicaDesligado() {
        assertTrue(ConfiguracaoEmail.de(Map.of()).isEmpty());
        assertTrue(ConfiguracaoEmail.de(Map.of("SMTP_HOST", "  ")).isEmpty());
    }

    @Test
    void gmailComStartTls() {
        ConfiguracaoEmail cfg = ConfiguracaoEmail.de(Map.of(
                "SMTP_HOST", "smtp.gmail.com", "SMTP_USUARIO", "eu@gmail.com", "SMTP_SENHA", "abcd efgh",
                "FRONT_URL", "https://chutometro.dev.br/")).orElseThrow();

        assertEquals(587, cfg.porta());
        assertEquals("eu@gmail.com", cfg.remetente(), "remetente padrão é o usuário");
        assertTrue(cfg.autenticado());
        assertEquals("https://chutometro.dev.br/#/redefinir-senha?token=abc", cfg.linkRedefinicao("abc"));
        assertFalse(cfg.toString().contains("abcd"), "a senha não vai para o log");

        Properties p = cfg.propriedades();
        assertEquals("smtp.gmail.com", p.get("mail.smtp.host"));
        assertEquals("true", p.get("mail.smtp.starttls.enable"));
        assertEquals("true", p.get("mail.smtp.auth"));
    }

    @Test
    void sslDireto() {
        Properties p = ConfiguracaoEmail.de(Map.of("SMTP_HOST", "smtp.x", "SMTP_PORTA", "465", "SMTP_SSL", "true",
                "SMTP_REMETENTE", "nao-responda@x")).orElseThrow().propriedades();

        assertEquals("smtps", p.get("mail.transport.protocol"));
        assertEquals("465", p.get("mail.smtps.port"));
        assertEquals("false", p.get("mail.smtps.auth"), "sem usuário, sem autenticação");
    }

    @Test
    void exigeRemetenteEPortaValida() {
        assertThrows(IllegalStateException.class, () -> ConfiguracaoEmail.de(Map.of("SMTP_HOST", "smtp.x")));
        assertThrows(IllegalStateException.class,
                () -> ConfiguracaoEmail.de(Map.of("SMTP_HOST", "smtp.x", "SMTP_USUARIO", "a", "SMTP_PORTA", "x")));
    }

    @Test
    void emailTemOLinkEEscapaOHtml() {
        EmailRedefinicaoSenha email = EmailRedefinicaoSenha.montar("<ANA>", "https://f/redefinir-senha?token=t", 30, false);

        assertTrue(email.assunto().startsWith("[TESTES]"));
        assertTrue(email.texto().contains("https://f/redefinir-senha?token=t"));
        assertTrue(email.texto().contains("30 minutos"));
        assertTrue(email.html().contains("&lt;ANA&gt;"));
        assertFalse(email.html().contains("<ANA>"));
        assertFalse(EmailRedefinicaoSenha.montar("ANA", "l", 30, true).assunto().startsWith("["));
    }
}
