package com.planningapp.service.perfil;

import com.planningapp.service.exception.DadosInvalidosException;

import java.util.Arrays;

/**
 * Confere a foto enviada pelo próprio conteúdo (os primeiros bytes), não pelo Content-Type que o cliente declarou:
 * a foto é servida de volta com o tipo detectado aqui, então nada que não seja JPEG, PNG ou WebP é aceito.
 */
public final class ImagemPerfil {

    /** O front reduz para 256x256 (dá uns 20 KB); o limite é folgado para quem enviar pela API. */
    public static final int TAMANHO_MAXIMO = 512 * 1024;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private ImagemPerfil() {
    }

    /** O media type da imagem; {@link DadosInvalidosException} se estiver vazia, grande demais ou não for imagem aceita. */
    public static String tipo(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new DadosInvalidosException("Envie a imagem da foto");
        }
        if (conteudo.length > TAMANHO_MAXIMO) {
            throw new DadosInvalidosException("A foto deve ter no máximo " + TAMANHO_MAXIMO / 1024 + " KB");
        }
        if (comeca(conteudo, 0, JPEG)) {
            return "image/jpeg";
        }
        if (comeca(conteudo, 0, PNG)) {
            return "image/png";
        }
        if (comeca(conteudo, 0, RIFF) && comeca(conteudo, 8, WEBP)) {
            return "image/webp";
        }
        throw new DadosInvalidosException("A foto deve ser uma imagem JPEG, PNG ou WebP");
    }

    private static boolean comeca(byte[] conteudo, int inicio, byte[] assinatura) {
        return conteudo.length >= inicio + assinatura.length
                && Arrays.equals(conteudo, inicio, inicio + assinatura.length, assinatura, 0, assinatura.length);
    }
}
