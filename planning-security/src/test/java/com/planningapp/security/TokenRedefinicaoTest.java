package com.planningapp.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenRedefinicaoTest {

    @Test
    void tokenServeNaUrlETemOsBytesEsperados() {
        String token = TokenRedefinicao.gerar();

        assertEquals(43, token.length(), "32 bytes em Base64 sem padding");
        assertTrue(token.matches("[A-Za-z0-9_-]+"), token);
        assertNotEquals(token, TokenRedefinicao.gerar());
    }

    @Test
    void hashEhEstavelEDiferenteDoToken() {
        String token = TokenRedefinicao.gerar();
        String hash = TokenRedefinicao.hash(token);

        assertEquals(64, hash.length());
        assertEquals(hash, TokenRedefinicao.hash(token));
        assertEquals(hash, TokenRedefinicao.hash(" " + token + "\n"), "colar o link com espaço não atrapalha");
        assertNotEquals(hash, TokenRedefinicao.hash(TokenRedefinicao.gerar()));
    }
}
