package com.planningapp.security;

import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenhaLegadaTest {

    /**
     * Gerado com {@code new BCryptPasswordEncoder().encode("segredo123")} do spring-security-crypto 6.2.4, a
     * versão do Planning original.
     */
    private static final String DO_SPRING = "$2a$10$lcVDB6/qw2MtSN2bAck.4eztXbbyxIlPt7kUTfxIHPSH1BycI8hEG";

    @Test
    void confereOHashGravadoPeloSpring() {
        assertTrue(SenhaLegada.ehBcrypt(DO_SPRING));
        assertTrue(SenhaLegada.confere("segredo123", DO_SPRING));
        assertFalse(SenhaLegada.confere("outra", DO_SPRING));
    }

    @Test
    void ignoraEspacosNasPontasComoOOriginal() {
        assertTrue(SenhaLegada.confere("  segredo123 ", DO_SPRING));
    }

    @Test
    void aceitaAsVariantes2bE2y() {
        String hash = BCrypt.hashpw("abc123", BCrypt.gensalt(4));
        assertTrue(SenhaLegada.confere("abc123", "$2b$" + hash.substring(4)));
        assertTrue(SenhaLegada.confere("abc123", "$2y$" + hash.substring(4)));
    }

    @Test
    void hashPbkdf2NaoEhBcrypt() {
        assertFalse(SenhaLegada.ehBcrypt("PBKDF2WithHmacSHA256:2048:abc:def"));
        assertFalse(SenhaLegada.confere("abc", "PBKDF2WithHmacSHA256:2048:abc:def"));
        assertFalse(SenhaLegada.ehBcrypt(null));
    }
}
