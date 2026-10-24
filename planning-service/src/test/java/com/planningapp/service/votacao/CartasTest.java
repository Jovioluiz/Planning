package com.planningapp.service.votacao;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartasTest {

    @Test
    void aceitaFibonacciECafe() {
        for (int carta : new int[]{0, 1, 2, 3, 5, 8, 13, 21}) {
            assertTrue(Cartas.pontosValidos(carta), "carta " + carta);
        }
    }

    @Test
    void recusaCartasForaDoBaralho() {
        for (int carta : new int[]{-1, 4, 6, 20, 34}) {
            assertFalse(Cartas.pontosValidos(carta), "carta " + carta);
        }
    }

    @Test
    void horasEntre1e16() {
        assertTrue(Cartas.horasValidas(1));
        assertTrue(Cartas.horasValidas(16));
        assertFalse(Cartas.horasValidas(0));
        assertFalse(Cartas.horasValidas(17));
    }

    @Test
    void cartaMaisProximaEmpataParaCima() {
        assertEquals(5, Cartas.cartaMaisProxima(4.0));
        assertEquals(5, Cartas.cartaMaisProxima(5.3));
        assertEquals(8, Cartas.cartaMaisProxima(7.0));
        assertEquals(1, Cartas.cartaMaisProxima(0.2));
        assertEquals(21, Cartas.cartaMaisProxima(40));
    }
}
