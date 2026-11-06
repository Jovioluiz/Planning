package com.planningapp.service.perfil;

import com.planningapp.service.exception.DadosInvalidosException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImagemPerfilTest {

    @Test
    void detectaOTipoPeloConteudo() {
        assertEquals("image/jpeg", ImagemPerfil.tipo(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0}));
        assertEquals("image/png", ImagemPerfil.tipo(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0}));
        assertEquals("image/webp", ImagemPerfil.tipo("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void recusaOQueNaoEhImagemAceita() {
        assertThrows(DadosInvalidosException.class, () -> ImagemPerfil.tipo(null));
        assertThrows(DadosInvalidosException.class, () -> ImagemPerfil.tipo(new byte[0]));
        assertThrows(DadosInvalidosException.class,
                () -> ImagemPerfil.tipo("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(DadosInvalidosException.class, () -> ImagemPerfil.tipo("GIF89a".getBytes(StandardCharsets.UTF_8)));
        assertThrows(DadosInvalidosException.class, () -> ImagemPerfil.tipo("RIFF".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void recusaFotoGrandeDemais() {
        byte[] grande = new byte[ImagemPerfil.TAMANHO_MAXIMO + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;

        assertThrows(DadosInvalidosException.class, () -> ImagemPerfil.tipo(grande));
    }
}
