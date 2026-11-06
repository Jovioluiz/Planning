package com.planningapp.service.email;

import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Envia e-mails pelo SMTP de {@link ConfiguracaoEmail}. Assíncrono: a requisição do "Esqueci minha senha" responde
 * na hora (e no mesmo tempo, exista ou não o usuário), e uma falha do SMTP só aparece no log.
 */
@Stateless
@PermitAll
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class EnvioEmail {

    private static final Logger LOG = Logger.getLogger(EnvioEmail.class.getName());

    public Optional<ConfiguracaoEmail> configuracao() {
        return ConfiguracaoEmail.doAmbiente();
    }

    @Asynchronous
    public void enviar(String para, String assunto, String texto, String html) {
        Optional<ConfiguracaoEmail> configuracao = ConfiguracaoEmail.doAmbiente();
        if (configuracao.isEmpty()) {
            LOG.warning("E-mail para " + para + " não enviado: SMTP_HOST não configurado");
            return;
        }
        ConfiguracaoEmail cfg = configuracao.get();
        try {
            Session sessao = Session.getInstance(cfg.propriedades(), cfg.autenticado() ? new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(cfg.usuario(), cfg.senha());
                }
            } : null);

            MimeMessage mensagem = new MimeMessage(sessao);
            mensagem.setFrom(new InternetAddress(cfg.remetente(), "Chutômetro", StandardCharsets.UTF_8.name()));
            mensagem.setRecipients(Message.RecipientType.TO, InternetAddress.parse(para));
            mensagem.setSubject(assunto, StandardCharsets.UTF_8.name());
            mensagem.setSentDate(new Date());

            MimeBodyPart parteTexto = new MimeBodyPart();
            parteTexto.setText(texto, StandardCharsets.UTF_8.name());
            MimeBodyPart parteHtml = new MimeBodyPart();
            parteHtml.setContent(html, "text/html; charset=UTF-8");
            mensagem.setContent(new MimeMultipart("alternative", parteTexto, parteHtml));

            Transport.send(mensagem);
            LOG.info("E-mail \"" + assunto + "\" enviado para " + para);
        } catch (MessagingException | UnsupportedEncodingException e) {
            LOG.log(Level.SEVERE, "Falha ao enviar e-mail para " + para + " por " + cfg, e);
        }
    }
}
