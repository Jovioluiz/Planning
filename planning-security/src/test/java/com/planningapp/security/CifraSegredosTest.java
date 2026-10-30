package com.planningapp.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CifraSegredosTest {

    private static final String CHAVE_API = "2f8a6c0e4b1d9f7a3c5e8b0d2f4a6c8e0b2d4f6a";

    private final CifraSegredos cifra = new CifraSegredos("segredo-de-teste-com-mais-de-32-bytes!!");

    @Test
    void decifraOQueCifrou() {
        String gravado = cifra.cifrar(CHAVE_API);

        assertTrue(cifra.estaCifrado(gravado));
        assertFalse(gravado.contains(CHAVE_API));
        assertEquals(CHAVE_API, cifra.decifrar(gravado));
    }

    @Test
    void ivAleatorioGeraCifradosDiferentesParaOMesmoTexto() {
        assertNotEquals(cifra.cifrar(CHAVE_API), cifra.cifrar(CHAVE_API));
    }

    @Test
    void cabeNaColunaChaveApi() {
        // configuracoes_redmine.chave_api é VARCHAR(512) (changelog 007)
        assertTrue(cifra.cifrar(CHAVE_API).length() <= 512);
    }

    @Test
    void textoPuroGravadoAntesDaCifragemVoltaComoEsta() {
        assertFalse(cifra.estaCifrado(CHAVE_API));
        assertEquals(CHAVE_API, cifra.decifrar(CHAVE_API));
    }

    @Test
    void outroSegredoNaoDecifra() {
        String gravado = cifra.cifrar(CHAVE_API);
        CifraSegredos outra = new CifraSegredos("outro-segredo-de-teste-com-mais-de-32-bytes");

        assertThrows(CifraSegredos.SegredoIlegivelException.class, () -> outra.decifrar(gravado));
    }

    @Test
    void valorAdulteradoNaoDecifra() {
        String gravado = cifra.cifrar(CHAVE_API);
        char ultimo = gravado.charAt(gravado.length() - 3);
        String adulterado = gravado.substring(0, gravado.length() - 3) + (ultimo == 'A' ? 'B' : 'A')
                + gravado.substring(gravado.length() - 2);

        assertThrows(CifraSegredos.SegredoIlegivelException.class, () -> cifra.decifrar(adulterado));
        assertThrows(CifraSegredos.SegredoIlegivelException.class, () -> cifra.decifrar("v1:!!nao-e-base64"));
        assertThrows(CifraSegredos.SegredoIlegivelException.class, () -> cifra.decifrar("v1:AAAA"));
    }
}
