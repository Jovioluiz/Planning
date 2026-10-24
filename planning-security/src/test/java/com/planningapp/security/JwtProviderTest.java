package com.planningapp.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtProviderTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-bytes!!";

    private final JwtProvider provider = new JwtProvider(SEGREDO, Duration.ofMinutes(5));

    @Test
    void tokenGeradoEhValidoComUsuarioEPapeis() {
        String token = provider.gerar("jovio", Set.of(Papeis.ADMIN)).token();

        Optional<TokenValidado> validado = provider.validar(token);

        assertTrue(validado.isPresent());
        assertEquals("jovio", validado.get().usuario());
        assertEquals(Set.of(Papeis.ADMIN), validado.get().papeis());
    }

    @Test
    void rejeitaTokenAdulterado() {
        String token = provider.gerar("jovio", Set.of(Papeis.JOGADOR)).token();
        String[] partes = token.split("\\.");
        String adulterado = partes[0] + "." + partes[1] + "x." + partes[2];

        assertTrue(provider.validar(adulterado).isEmpty());
    }

    @Test
    void rejeitaTokenAssinadoComOutroSegredo() {
        JwtProvider outro = new JwtProvider("outro-segredo-tambem-com-mais-de-32-bytes", Duration.ofMinutes(5));
        String token = outro.gerar("jovio", Set.of(Papeis.ADMIN)).token();

        assertTrue(provider.validar(token).isEmpty());
    }

    @Test
    void rejeitaTokenExpirado() {
        JwtProvider expirado = new JwtProvider(SEGREDO, Duration.ofSeconds(-1));
        String token = expirado.gerar("jovio", Set.of(Papeis.ADMIN)).token();

        assertTrue(provider.validar(token).isEmpty());
    }

    @Test
    void rejeitaLixo() {
        assertTrue(provider.validar("isso-nao-e-um-jwt").isEmpty());
    }

    @Test
    void exigeSegredoDeTamanhoMinimo() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProvider("curto", Duration.ofMinutes(5)));
    }
}
