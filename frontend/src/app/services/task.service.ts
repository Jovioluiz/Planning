import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { map } from 'rxjs/operators';
import { API, EnvioRedmineDTO, TarefaDTO, paraITask } from './api-jakarta';

/** Formato que os componentes usam (o do backend antigo); vem de {@link paraITask}. */
export interface ITask {
  id: number;
  numero: string;
  titulo: string;
  descricao: string;
  status: string | null;
  estimada: boolean;
  liberada: boolean;
  pontosRevelados?: boolean;
  horasReveladas?: boolean;
  horasLiberadas?: boolean;
  horasTesteLiberadas?: boolean;
  horasTesteReveladas?: boolean;
  sprint?: string;
  liberadaEm?: string;
  rodadaAtual?: number;
  pulada?: boolean;
  pontosFinais?: number | null;
  horasFinais?: number | null;
  horasTesteFinais?: number | null;
  dadosExtras?: Record<string, any>;
  doRedmine?: boolean;
}

/** Valores escolhidos pelo moderador ao finalizar a tarefa. */
export interface ValoresFinais {
  pontosFinais: number | null;
  horasFinais: number | null;
  horasTesteFinais: number | null;
}

/**
 * Tarefas da sala ativa (sessionStorage.salaId). No backend novo toda tarefa pertence a uma sala; os métodos sem
 * "Sala" no nome continuam existindo só porque os componentes os chamam, e também usam a sala ativa.
 */
@Injectable({
  providedIn: 'root'
})
export class TaskService {

  constructor(private http: HttpClient) {}

  // O interceptor funcional falha quando HttpClient é chamado fora do contexto de
  // injeção do Angular (ex: callbacks de PapaParse, FileReader, etc.).
  // Este helper garante que o token seja sempre enviado independente do contexto.
  private get authOptions() {
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('token') : null;
    return token ? { headers: { Authorization: `Bearer ${token}` } } : {};
  }

  private get salaId(): string | null {
    return typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('salaId') : null;
  }

  private tarefas(caminho = ''): string {
    return `${API}/salas/${this.salaId}/tarefas${caminho}`;
  }

  private semSala<T>(): Observable<T> {
    return throwError(() => new Error('Entre em uma sala pelo link do moderador.'));
  }

  getTaskById(id: string): Observable<ITask> {
    if (!this.salaId) return this.semSala();
    return this.http.get<TarefaDTO>(this.tarefas(`/${id}`), this.authOptions).pipe(map(paraITask));
  }

  private listar(filtro: (t: TarefaDTO) => boolean): Observable<ITask[]> {
    if (!this.salaId) return of([]);
    return this.http.get<TarefaDTO[]>(this.tarefas(), this.authOptions).pipe(
      map(lista => lista.filter(filtro).map(paraITask))
    );
  }

  getTarefasFilaSala(): Observable<ITask[]> {
    return this.listar(t => t.status === 'FILA');
  }

  getTarefasLiberadasSala(): Observable<ITask[]> {
    return this.listar(t => t.status === 'LIBERADA');
  }

  /** "Votadas" no original = estimada = true, o que incluía as puladas. */
  getTarefasVotadasSala(): Observable<ITask[]> {
    return this.listar(t => t.status === 'ESTIMADA' || t.status === 'PULADA');
  }

  getTarefasLiberadas(): Observable<ITask[]> {
    return this.getTarefasLiberadasSala();
  }

  /** O front lê res.message; o backend novo devolve { mensagem, importadas, ignoradas }. */
  importarCSVSala(tarefas: any[]): Observable<any> {
    if (!this.salaId) return this.semSala();
    const itens = tarefas.map(t => ({ ...t, numero: String(t.numero) }));
    return this.http.post<any>(this.tarefas('/importar'), itens, this.authOptions).pipe(
      map(res => ({ ...res, message: res?.mensagem }))
    );
  }

  /** Chamados da consulta do Redmine configurada em /redmine (o backend busca com a chave de lá). */
  importarRedmine(): Observable<any> {
    if (!this.salaId) return this.semSala();
    return this.http.post<any>(this.tarefas('/importar-redmine'), {}, this.authOptions).pipe(
      map(res => ({ ...res, message: res?.mensagem }))
    );
  }

  liberarTarefaSala(id: string): Observable<any> {
    return this.http.post(this.tarefas(`/${id}/liberar`), {}, this.authOptions);
  }

  /** Horas inteiras: os votos são de 1 a 16 e o backend novo guarda inteiro (a média 4,5 vira 5). */
  finalizarTarefaSala(id: string, valores?: ValoresFinais): Observable<any> {
    const inteiro = (v: number | null | undefined) => (v === null || v === undefined ? null : Math.round(v));
    const corpo = {
      pontosFinais: valores?.pontosFinais ?? null,
      horasFinais: inteiro(valores?.horasFinais),
      horasTesteFinais: inteiro(valores?.horasTesteFinais)
    };
    return this.http.post(this.tarefas(`/${id}/finalizar`), corpo, this.authOptions);
  }

  /** Grava a estimativa final no chamado do Redmine; só para tarefas com doRedmine. */
  enviarRedmineSala(id: string): Observable<EnvioRedmineDTO> {
    return this.http.post<EnvioRedmineDTO>(this.tarefas(`/${id}/enviar-redmine`), {}, this.authOptions);
  }

  /** Relê o chamado no Redmine e grava na tarefa o título, a descrição e os campos como estão agora. */
  atualizarRedmineSala(id: string): Observable<ITask> {
    return this.http.post<TarefaDTO>(this.tarefas(`/${id}/atualizar-redmine`), {}, this.authOptions).pipe(map(paraITask));
  }

  /** Imagem anexada ao chamado, pelo nome do arquivo; o backend baixa do Redmine com a chave do moderador. */
  anexoRedmineSala(id: string, nome: string): Observable<Blob> {
    return this.http.get(this.tarefas(`/${id}/anexos-redmine/${encodeURIComponent(nome)}`),
      { ...this.authOptions, responseType: 'blob' });
  }

  pularTarefaSala(id: string): Observable<any> {
    return this.http.post(this.tarefas(`/${id}/pular`), {}, this.authOptions);
  }

  liberarHorasVotacaoSala(id: string): Observable<any> {
    return this.http.post(this.tarefas(`/${id}/votacao/liberar-horas`), {}, this.authOptions);
  }

  liberarHorasTesteSala(id: string): Observable<any> {
    return this.http.post(this.tarefas(`/${id}/votacao/liberar-horas-teste`), {}, this.authOptions);
  }

  removerParticipanteSala(taskId: string, participante: string): Observable<any> {
    return this.http.delete(this.tarefas(`/${taskId}/participantes/${encodeURIComponent(participante)}`), this.authOptions);
  }

  removerTarefaSala(id: string): Observable<any> {
    return this.http.delete(this.tarefas(`/${id}`), this.authOptions);
  }

  /** Só os ativos (não removidos), como o /participantes do original. */
  getParticipantesTarefa(taskId: string): Observable<string[]> {
    if (!this.salaId) return of([]);
    return this.http.get<{ usuario: string; ativo: boolean }[]>(this.tarefas(`/${taskId}/participantes`), this.authOptions).pipe(
      map(lista => lista.filter(p => p.ativo).map(p => p.usuario))
    );
  }

  /** Quem está conectado no WebSocket da sala, em qualquer nó do cluster. */
  getUsuariosOnline(): Observable<string[]> {
    if (!this.salaId) return of([]);
    return this.http.get<string[]>(`${API}/salas/${this.salaId}/online`, this.authOptions);
  }

  /**
   * No backend novo, estar na sala é estar conectado no WebSocket dela (o componente assina o tópico da sala
   * logo em seguida). Mantido para não mexer nos componentes.
   */
  registrarPresencaNaSala(_salaId: string): Observable<any> {
    return of({});
  }
}
