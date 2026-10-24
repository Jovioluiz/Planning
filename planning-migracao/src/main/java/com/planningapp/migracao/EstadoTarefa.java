package com.planningapp.migracao;

/**
 * Converte as flags da tarefa do Planning original no estado desta versão.
 *
 * <pre>
 *   original                                        esta versão
 *   pulada (também tinha estimada = true)       →   PULADA
 *   estimada                                    →   ESTIMADA
 *   liberada + pontosRevelados/horas...         →   LIBERADA + fase (PONTOS ... HORAS_REVELADAS)
 *   horasTesteLiberadas / horasTesteReveladas   →   faseHorasTeste (VOTACAO / REVELADA)
 *   nenhuma                                     →   FILA
 * </pre>
 *
 * @param fase           nula fora de LIBERADA
 * @param faseHorasTeste nula se a trilha de teste não foi liberada (ou fora de LIBERADA)
 */
public record EstadoTarefa(String status, String fase, String faseHorasTeste) {

    public static EstadoTarefa de(boolean liberada, boolean estimada, boolean pulada, boolean pontosRevelados,
                                  boolean horasLiberadas, boolean horasReveladas,
                                  boolean horasTesteLiberadas, boolean horasTesteReveladas) {
        if (pulada) {
            return new EstadoTarefa("PULADA", null, null);
        }
        if (estimada) {
            return new EstadoTarefa("ESTIMADA", null, null);
        }
        if (!liberada) {
            return new EstadoTarefa("FILA", null, null);
        }
        String fase;
        if (horasReveladas) {
            fase = "HORAS_REVELADAS";
        } else if (horasLiberadas) {
            fase = "HORAS";
        } else if (pontosRevelados) {
            fase = "PONTOS_REVELADOS";
        } else {
            fase = "PONTOS";
        }
        String teste = horasTesteReveladas ? "REVELADA" : horasTesteLiberadas ? "VOTACAO" : null;
        return new EstadoTarefa("LIBERADA", fase, teste);
    }
}
