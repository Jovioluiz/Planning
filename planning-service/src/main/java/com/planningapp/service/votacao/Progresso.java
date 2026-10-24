package com.planningapp.service.votacao;

import java.util.Collection;
import java.util.List;

/**
 * Quanto falta para uma fase terminar.
 *
 * @param aguardando   quem está online, deveria votar e ainda não votou
 * @param todosVotaram ninguém online está pendente e houve ao menos um voto. Quem votou e saiu continua
 *                     contando; quem nunca entrou na sala não trava a rodada
 */
public record Progresso(List<String> aguardando, boolean todosVotaram) {

    public static Progresso calcular(Collection<String> esperados, Collection<String> votaram) {
        List<String> aguardando = esperados.stream().filter(u -> !votaram.contains(u)).sorted().toList();
        return new Progresso(aguardando, !votaram.isEmpty() && aguardando.isEmpty());
    }
}
