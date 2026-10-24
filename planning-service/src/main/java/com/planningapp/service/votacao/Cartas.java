package com.planningapp.service.votacao;

import com.planningapp.model.Estimativa;

import java.util.List;

/** Baralho do Planning: Fibonacci para pontos (+ café) e 1 a 16 para horas. */
public final class Cartas {

    public static final List<Integer> PONTOS = List.of(1, 2, 3, 5, 8, 13, 21);
    public static final int HORAS_MINIMO = 1;
    public static final int HORAS_MAXIMO = 16;

    private Cartas() {
    }

    public static boolean pontosValidos(int pontos) {
        return pontos == Estimativa.CAFE || PONTOS.contains(pontos);
    }

    public static boolean horasValidas(int horas) {
        return horas >= HORAS_MINIMO && horas <= HORAS_MAXIMO;
    }

    /** Carta mais próxima do valor; em empate (ex.: 4 entre 3 e 5), fica com a maior, que é o mais seguro para estimar. */
    public static int cartaMaisProxima(double valor) {
        int melhor = PONTOS.getFirst();
        for (int carta : PONTOS) {
            if (Math.abs(carta - valor) <= Math.abs(melhor - valor)) {
                melhor = carta;
            }
        }
        return melhor;
    }
}
