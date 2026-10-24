package com.planningapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Valores finais decididos pelo moderador (normalmente a partir do resumo da votação). Todos opcionais, como no
 * Planning original: se todos votaram café, por exemplo, não há carta para sugerir.
 */
public record FinalizarTarefaRequest(
        @Min(0) @Max(100)
        Integer pontosFinais,
        @Min(value = 0, message = "As horas finais não podem ser negativas")
        Integer horasFinais,
        @Min(value = 0, message = "As horas de teste finais não podem ser negativas")
        Integer horasTesteFinais) {
}
