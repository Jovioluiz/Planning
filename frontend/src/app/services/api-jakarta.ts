/**
 * Contrato da API do planning-jakarta e a tradução para os formatos que os componentes já usavam com o backend
 * Spring Boot. Os componentes continuam recebendo ITask com flags (liberada, pontosRevelados...) e estimativas
 * com "🔒" para valores ocultos; só os services e este arquivo conhecem a API nova.
 */
import { environment } from '../../environments/environment';

/** Base da API REST: {apiUrl}/api (o apiUrl já inclui o context root /planning). */
export const API = `${environment.apiUrl}/api`;

// ─── DTOs da API nova ─────────────────────────────────────────

export interface TarefaDTO {
  id: number;
  numero: string;
  titulo: string;
  descricao: string | null;
  sprint: string | null;
  status: 'FILA' | 'LIBERADA' | 'ESTIMADA' | 'PULADA';
  fase: 'PONTOS' | 'PONTOS_REVELADOS' | 'HORAS' | 'HORAS_REVELADAS' | null;
  faseHorasTeste: 'VOTACAO' | 'REVELADA' | null;
  rodadaAtual: number;
  pontosFinais: number | null;
  horasFinais: number | null;
  horasTesteFinais: number | null;
  salaId: number;
  liberadaEm: string | null;
  encerradaEm: string | null;
  horasLiberadasEm: string | null;
  dadosExtras: Record<string, string> | null;
}

export interface VotoDTO {
  usuario: string;
  votouPontos: boolean;
  pontos: number | null;
  votouHoras: boolean;
  horas: number | null;
  rodada: number;
  segundosPontos: number | null;
  segundosHoras: number | null;
}

export interface VotoTesteDTO {
  usuario: string;
  votou: boolean;
  horas: number | null;
}

export interface VotacaoDTO {
  tarefaId: number;
  status: TarefaDTO['status'];
  fase: TarefaDTO['fase'];
  rodada: number;
  votos: VotoDTO[];
  aguardando: string[] | null;
  todosVotaram: boolean | null;
  faseHorasTeste: TarefaDTO['faseHorasTeste'];
  votosTeste: VotoTesteDTO[];
  aguardandoTeste: string[] | null;
  todosTestadoresVotaram: boolean | null;
  votosRodadaAnterior: VotoDTO[];
}

/** Evento do WebSocket da sala (ws://.../ws/salas/{id}?token=...). */
export interface EventoSala {
  tipo: string;
  salaId: number;
  tarefa?: TarefaDTO;
  usuario?: string;
  online?: string[];
  em: string;
}

// ─── Tradução para os formatos antigos ─────────────────────────

// Atenção: o JSON-B do backend omite campos nulos, então "sem valor" chega como undefined. Por isso as
// comparações aqui são com == null (pega null e undefined), nunca === null.
const OCULTO = '🔒';
const CAFE = '☕';

/** As flags do backend antigo, derivadas do status e das fases. */
export function paraITask(t: TarefaDTO): any {
  const encerrada = t.status === 'ESTIMADA' || t.status === 'PULADA';
  const fase = t.fase;
  return {
    id: t.id,
    numero: t.numero,
    titulo: t.titulo,
    descricao: t.descricao ?? '',
    sprint: t.sprint ?? '',
    status: t.status,
    liberada: t.status === 'LIBERADA',
    // No original, pular também marcava estimada = true (o front trata pulada à parte)
    estimada: encerrada,
    pulada: t.status === 'PULADA',
    pontosRevelados: t.status === 'ESTIMADA' || fase === 'PONTOS_REVELADOS' || fase === 'HORAS' || fase === 'HORAS_REVELADAS',
    horasLiberadas: t.status === 'ESTIMADA' || fase === 'HORAS' || fase === 'HORAS_REVELADAS',
    horasReveladas: t.status === 'ESTIMADA' || fase === 'HORAS_REVELADAS',
    horasTesteLiberadas: t.faseHorasTeste != null,
    horasTesteReveladas: t.faseHorasTeste === 'REVELADA',
    rodadaAtual: t.rodadaAtual,
    pontosFinais: t.pontosFinais ?? null,
    horasFinais: t.horasFinais ?? null,
    horasTesteFinais: t.horasTesteFinais ?? null,
    liberadaEm: t.liberadaEm ?? null,
    estimadaEm: t.encerradaEm ?? null,
    dadosExtras: t.dadosExtras ?? undefined
  };
}

/**
 * Votos no formato do antigo /estimativas/listar: { participante, pontos, horas, revealed, horasReveladas, rodada,
 * segundosPontos, segundosHoras }. Valor que o usuário não pode ver vem como "🔒" (votou, mas está oculto), café
 * como "☕", e horas não votadas como null — igual ao original, que o template já sabe mostrar.
 */
export function paraEstimativas(v: VotacaoDTO): any[] {
  const encerrada = v.status === 'ESTIMADA';
  const pontosRevelados = encerrada || v.fase === 'PONTOS_REVELADOS' || v.fase === 'HORAS' || v.fase === 'HORAS_REVELADAS';
  const horasReveladas = encerrada || v.fase === 'HORAS_REVELADAS';
  const converter = (voto: VotoDTO, revelado: boolean, horasRev: boolean) => ({
    participante: voto.usuario,
    pontos: !voto.votouPontos ? null : voto.pontos == null ? OCULTO : voto.pontos === 0 ? CAFE : voto.pontos,
    horas: !voto.votouHoras ? null : voto.horas == null ? OCULTO : voto.horas,
    revealed: revelado,
    horasReveladas: horasRev,
    rodada: voto.rodada ?? 1,
    segundosPontos: voto.segundosPontos ?? null,
    segundosHoras: voto.segundosHoras ?? null
  });
  return [
    ...(v.votosRodadaAnterior ?? []).map(voto => converter(voto, true, true)),
    ...v.votos.map(voto => converter(voto, pontosRevelados, horasReveladas))
  ];
}

/** Votos de teste no formato do antigo /estimativas/listar-teste. */
export function paraEstimativasTeste(v: VotacaoDTO): any[] {
  const reveladas = v.status === 'ESTIMADA' || v.faseHorasTeste === 'REVELADA';
  return (v.votosTeste ?? []).map(voto => ({
    participante: voto.usuario,
    horasTeste: !voto.votou ? null : voto.horas == null ? OCULTO : voto.horas,
    horasTesteReveladas: reveladas
  }));
}

/** O nome do evento no backend antigo (o front compara data.acao). */
const ACAO_ANTIGA: Record<string, string> = {
  PONTOS_REVELADOS: 'REVELAR_PONTOS',
  HORAS_REVELADAS: 'REVELAR_HORAS',
  HORAS_TESTE_REVELADAS: 'REVELAR_HORAS_TESTE',
  SALA_DESATIVADA: 'SALA_INATIVADA'
};

/**
 * Evento novo → mensagem no formato do tópico antigo ({ acao, taskId, participante, usuario }). Os eventos de
 * presença e de sala desativada iam para /topic/sala/{id}/sessoes; os de votação, para /topic/sala/{id}/estimativas.
 */
export function paraMensagemAntiga(evento: EventoSala, onlineAntes: string[]): { topico: 'sessoes' | 'estimativas'; dados: any }[] {
  if (evento.tipo === 'PRESENCA') {
    const online = evento.online ?? [];
    const usuario = evento.usuario ?? '';
    const conectou = online.includes(usuario) && !onlineAntes.includes(usuario);
    const saiu = !online.includes(usuario);
    if (!conectou && !saiu) {
      return [];
    }
    return [{ topico: 'sessoes', dados: { acao: conectou ? 'USUARIO_CONECTADO' : 'USUARIO_DESCONECTADO', usuario, online } }];
  }
  const acao = ACAO_ANTIGA[evento.tipo] ?? evento.tipo;
  const dados = { acao, taskId: evento.tarefa?.id, participante: evento.usuario, usuario: evento.usuario, salaId: evento.salaId };
  return [{ topico: acao === 'SALA_INATIVADA' ? 'sessoes' : 'estimativas', dados }];
}

/** O backend novo responde erros como { status, mensagem, detalhes }; o front lia err.error.message. */
export function mensagemDeErro(corpo: any): string | null {
  if (!corpo || typeof corpo !== 'object') return null;
  const base = corpo.mensagem ?? corpo.message ?? null;
  const detalhes: string[] = Array.isArray(corpo.detalhes) ? corpo.detalhes : [];
  if (base && detalhes.length) return `${base}: ${detalhes.join('; ')}`;
  return base;
}
