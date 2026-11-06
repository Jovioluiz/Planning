package com.planningapp.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token do link "Esqueci minha senha": 32 bytes aleatórios em Base64 URL-safe (vai na URL). O banco guarda só o
 * SHA-256 dele ({@link #hash}): quem ler o banco (ou um backup) não consegue montar o link.
 *
 * <p>SHA-256 sem sal basta aqui (diferente de senha): o token já tem 256 bits de entropia e vale por minutos.</p>
 */
public final class TokenRedefinicao {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenRedefinicao() {
    }

    /** Um token novo, para ir no link. */
    public static String gerar() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** O que vai para o banco: SHA-256 em hexadecimal (64 caracteres). */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
