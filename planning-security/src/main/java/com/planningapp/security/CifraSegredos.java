package com.planningapp.security;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Cifra segredos de terceiros que a aplicação precisa ler de volta (hoje, a chave da API do Redmine de cada
 * moderador), para não ficarem em texto puro no banco nem nos backups. Senha de usuário não passa por aqui: essa
 * vira hash e nunca é lida de volta.
 *
 * <p>AES-256-GCM, com IV aleatório de 12 bytes por valor. O texto gravado é {@code v1:} + Base64(IV + cifrado +
 * tag); o prefixo separa o cifrado do texto puro gravado antes da cifragem, e permite trocar o formato depois.</p>
 *
 * <p>A chave AES é o SHA-256 de {@code REDMINE_SEGREDO}. Sem ela, usa o {@code JWT_SECRET} (avisando no log),
 * para quem já está no ar não precisar configurar nada; e sem nenhum dos dois, um segredo de desenvolvimento.
 * Trocar o segredo torna ilegíveis os valores já cifrados: o moderador precisa informar a chave do Redmine de
 * novo.</p>
 */
@ApplicationScoped
public class CifraSegredos {

    private static final Logger LOG = Logger.getLogger(CifraSegredos.class.getName());
    private static final String PREFIXO = "v1:";
    private static final String SEGREDO_DEV = "planning-jakarta-dev-inseguro-troque-em-producao!";
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private SecretKeySpec chave;

    /** Usado pelo CDI; o segredo é lido em {@link #iniciar()}. */
    public CifraSegredos() {
    }

    CifraSegredos(String segredo) {
        configurar(segredo);
    }

    @PostConstruct
    void iniciar() {
        String segredo = System.getenv("REDMINE_SEGREDO");
        if (segredo == null || segredo.isBlank()) {
            segredo = System.getenv("JWT_SECRET");
            if (segredo == null || segredo.isBlank()) {
                LOG.warning("REDMINE_SEGREDO e JWT_SECRET não definidos: cifrando com o segredo de desenvolvimento. "
                        + "NÃO use assim em produção.");
                segredo = SEGREDO_DEV;
            } else {
                LOG.info("REDMINE_SEGREDO não definido: a chave do Redmine é cifrada com o JWT_SECRET");
            }
        }
        configurar(segredo);
    }

    private void configurar(String segredo) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8));
            this.chave = new SecretKeySpec(hash, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    public String cifrar(String texto) {
        byte[] iv = new byte[TAMANHO_IV];
        ALEATORIO.nextBytes(iv);
        try {
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] tudo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIXO + Base64.getEncoder().encodeToString(tudo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar", e);
        }
    }

    /**
     * Valor gravado antes da cifragem (sem o prefixo) volta como está.
     *
     * @throws SegredoIlegivelException se o valor foi cifrado com outro segredo ou está corrompido
     */
    public String decifrar(String gravado) {
        if (!estaCifrado(gravado)) {
            return gravado;
        }
        try {
            byte[] tudo = Base64.getDecoder().decode(gravado.substring(PREFIXO.length()));
            if (tudo.length <= TAMANHO_IV) {
                throw new SegredoIlegivelException("Valor cifrado incompleto");
            }
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, tudo, 0, TAMANHO_IV));
            byte[] texto = cifra.doFinal(tudo, TAMANHO_IV, tudo.length - TAMANHO_IV);
            return new String(texto, StandardCharsets.UTF_8);
        } catch (AEADBadTagException | IllegalArgumentException e) {
            throw new SegredoIlegivelException("Valor cifrado com outro segredo ou corrompido");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar", e);
        }
    }

    public boolean estaCifrado(String gravado) {
        return gravado != null && gravado.startsWith(PREFIXO);
    }

    /** O segredo mudou desde que o valor foi cifrado, ou o valor no banco foi alterado. */
    public static class SegredoIlegivelException extends RuntimeException {
        public SegredoIlegivelException(String mensagem) {
            super(mensagem);
        }
    }
}
