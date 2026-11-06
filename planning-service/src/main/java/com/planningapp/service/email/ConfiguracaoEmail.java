package com.planningapp.service.email;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Servidor SMTP usado para mandar o link do "Esqueci minha senha", lido das variáveis de ambiente:
 *
 * <ul>
 *   <li>{@code SMTP_HOST} (sem ele, o envio de e-mail fica desligado), {@code SMTP_PORTA} (padrão 587);</li>
 *   <li>{@code SMTP_USUARIO}, {@code SMTP_SENHA} (no Gmail, uma "senha de app");</li>
 *   <li>{@code SMTP_REMETENTE} (padrão: o {@code SMTP_USUARIO}); {@code SMTP_SSL=true} para SSL direto
 *       (porta 465), senão STARTTLS;</li>
 *   <li>{@code FRONT_URL}: endereço do front que vai no link (padrão http://localhost:4200).</li>
 * </ul>
 */
public record ConfiguracaoEmail(String host, int porta, String usuario, String senha, String remetente,
                                boolean ssl, String urlFront) {

    private static final int TIMEOUT_MS = 15_000;

    public static Optional<ConfiguracaoEmail> doAmbiente() {
        return de(System.getenv());
    }

    static Optional<ConfiguracaoEmail> de(Map<String, String> env) {
        String host = valor(env, "SMTP_HOST", null);
        if (host == null) {
            return Optional.empty();
        }
        String usuario = valor(env, "SMTP_USUARIO", null);
        int porta;
        try {
            porta = Integer.parseInt(valor(env, "SMTP_PORTA", "587"));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("SMTP_PORTA inválida: " + env.get("SMTP_PORTA"), e);
        }
        String remetente = valor(env, "SMTP_REMETENTE", usuario);
        if (remetente == null) {
            throw new IllegalStateException("Defina SMTP_REMETENTE (ou SMTP_USUARIO)");
        }
        String front = valor(env, "FRONT_URL", "http://localhost:4200").replaceAll("/+$", "");
        return Optional.of(new ConfiguracaoEmail(host, porta, usuario, valor(env, "SMTP_SENHA", null), remetente,
                Boolean.parseBoolean(valor(env, "SMTP_SSL", "false")), front));
    }

    public boolean autenticado() {
        return usuario != null;
    }

    /** Propriedades do Jakarta Mail (Angus Mail, no WildFly). */
    public Properties propriedades() {
        Properties p = new Properties();
        String protocolo = ssl ? "smtps" : "smtp";
        p.put("mail.transport.protocol", protocolo);
        p.put("mail." + protocolo + ".host", host);
        p.put("mail." + protocolo + ".port", String.valueOf(porta));
        p.put("mail." + protocolo + ".auth", String.valueOf(autenticado()));
        p.put("mail." + protocolo + ".connectiontimeout", String.valueOf(TIMEOUT_MS));
        p.put("mail." + protocolo + ".timeout", String.valueOf(TIMEOUT_MS));
        p.put("mail." + protocolo + ".writetimeout", String.valueOf(TIMEOUT_MS));
        if (!ssl) {
            p.put("mail.smtp.starttls.enable", "true");
            if (autenticado()) {
                // Com usuário e senha, nunca em texto aberto
                p.put("mail.smtp.starttls.required", "true");
            }
        }
        return p;
    }

    /**
     * O link que vai no e-mail. O front usa rotas com "#" (HashLocationStrategy, no main.ts): sem ele, o Angular não
     * acha a rota e manda para o login. O token é Base64 URL-safe: não precisa de escape.
     */
    public String linkRedefinicao(String token) {
        return urlFront + "/#/redefinir-senha?token=" + token;
    }

    private static String valor(Map<String, String> env, String nome, String padrao) {
        String v = env.get(nome);
        return v == null || v.isBlank() ? padrao : v.trim();
    }

    @Override
    public String toString() {
        // Sem a senha (o toString padrão do record a imprimiria)
        return "ConfiguracaoEmail[" + host + ":" + porta + ", ssl=" + ssl + ", remetente=" + remetente + "]";
    }
}
