import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ITask {
  id: number;
  numero: number;
  titulo: string;
  descricao: string;
  prioridade: string | null;
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
  dadosExtras?: Record<string, any>;
}

@Injectable({
  providedIn: 'root'
})
export class TaskService {
  private url = `${environment.apiUrl}/api/tasks`;

  constructor(private http: HttpClient) {}

  // O interceptor funcional falha quando HttpClient é chamado fora do contexto de
  // injeção do Angular (ex: callbacks de PapaParse, FileReader, etc.).
  // Este helper garante que o token seja sempre enviado independente do contexto.
  private get authOptions() {
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('token') : null;
    return token ? { headers: { Authorization: `Bearer ${token}` } } : {};
  }

  buscarTarefaAtiva(): Observable<ITask> {
    return this.http.get<ITask>(`${this.url}/ativa`, this.authOptions);
  }

  getTaskById(id: string): Observable<ITask> {
    return this.http.get<ITask>(`${this.url}/${id}`, this.authOptions);
  }

  liberarTarefa(id: string): Observable<any> {
    return this.http.post(`${this.url}/${id}/liberar`, {}, this.authOptions);
  }

  getTarefasLiberadas(): Observable<ITask[]> {
    return this.http.get<ITask[]>(`${this.url}/liberadas`, this.authOptions);
  }

  getTarefasVotadas(): Observable<ITask[]> {
    return this.http.get<ITask[]>(`${this.url}/votadas`, this.authOptions);
  }

  getTarefasFila(): Observable<ITask[]> {
    return this.http.get<ITask[]>(`${this.url}/fila`, this.authOptions);
  }

  importarCSV(tarefas: any[]): Observable<any> {
    return this.http.post(`${this.url}/importar`, tarefas, this.authOptions);
  }

  removerTarefa(id: string): Observable<any> {
    return this.http.delete(`${this.url}/excluirTarefa/${id}`, this.authOptions);
  }

  getJogadores(): Observable<string[]> {
    return this.http.get<string[]>(`${environment.apiUrl}/api/auth/jogadores`, this.authOptions);
  }

  getParticipantesTarefa(taskId: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.url}/${taskId}/participantes`, this.authOptions);
  }

  finalizarTarefa(id: string): Observable<any> {
    return this.http.post(`${this.url}/${id}/finalizar`, {}, this.authOptions);
  }

  pularTarefa(id: string): Observable<any> {
    return this.http.post(`${this.url}/${id}/pular`, {}, this.authOptions);
  }

  liberarHorasVotacao(id: string): Observable<any> {
    return this.http.post(`${this.url}/${id}/liberar-horas`, {}, this.authOptions);
  }

  liberarHorasTeste(id: string): Observable<any> {
    return this.http.post(`${this.url}/${id}/liberar-horas-teste`, {}, this.authOptions);
  }

  getSprints(): Observable<string[]> {
    return this.http.get<string[]>(`${this.url}/sprints`, this.authOptions);
  }

  removerParticipante(taskId: string, participante: string): Observable<any> {
    return this.http.delete(
      `${this.url}/${taskId}/participantes/${encodeURIComponent(participante)}`,
      this.authOptions
    );
  }

  getUsuariosOnline(): Observable<string[]> {
    return this.http.get<string[]>(`${environment.apiUrl}/api/sessoes/online`, this.authOptions);
  }

  // ─── Métodos sala-scoped ──────────────────────────────────────

  private get salaPrefix(): string | null {
    const salaId = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('salaId') : null;
    return salaId ? `${environment.apiUrl}/api/salas/${salaId}/tasks` : null;
  }

  getTarefasFilaSala(): Observable<ITask[]> {
    const url = this.salaPrefix;
    if (!url) return this.getTarefasFila();
    return this.http.get<ITask[]>(`${url}/fila`, this.authOptions);
  }

  getTarefasLiberadasSala(): Observable<ITask[]> {
    const url = this.salaPrefix;
    if (!url) return this.getTarefasLiberadas();
    return this.http.get<ITask[]>(`${url}/liberadas`, this.authOptions);
  }

  getTarefasVotadasSala(): Observable<ITask[]> {
    const url = this.salaPrefix;
    if (!url) return this.getTarefasVotadas();
    return this.http.get<ITask[]>(`${url}/votadas`, this.authOptions);
  }

  importarCSVSala(tarefas: any[]): Observable<any> {
    const url = this.salaPrefix;
    if (!url) return this.importarCSV(tarefas);
    return this.http.post(`${url}/importar`, tarefas, this.authOptions);
  }

  liberarTarefaSala(id: string): Observable<any> {
    const url = this.salaPrefix;
    if (!url) return this.liberarTarefa(id);
    return this.http.post(`${url}/${id}/liberar`, {}, this.authOptions);
  }

  finalizarTarefaSala(id: string): Observable<any> {
    const url = this.salaPrefix;
    if (!url) return this.finalizarTarefa(id);
    return this.http.post(`${url}/${id}/finalizar`, {}, this.authOptions);
  }

  pularTarefaSala(id: string): Observable<any> {
    const url = this.salaPrefix;
    if (!url) return this.pularTarefa(id);
    return this.http.post(`${url}/${id}/pular`, {}, this.authOptions);
  }

  liberarHorasVotacaoSala(id: string): Observable<any> {
    const url = this.salaPrefix;
    if (!url) return this.liberarHorasVotacao(id);
    return this.http.post(`${url}/${id}/liberar-horas`, {}, this.authOptions);
  }

  liberarHorasTesteSala(id: string): Observable<any> {
    const url = this.salaPrefix;
    if (!url) return this.liberarHorasTeste(id);
    return this.http.post(`${url}/${id}/liberar-horas-teste`, {}, this.authOptions);
  }

  removerParticipanteSala(taskId: string, participante: string): Observable<any> {
    const url = this.salaPrefix;
    if (!url) return this.removerParticipante(taskId, participante);
    return this.http.delete(
      `${url}/${taskId}/participantes/${encodeURIComponent(participante)}`,
      this.authOptions
    );
  }

  removerTarefaSala(id: string): Observable<any> {
    const url = this.salaPrefix;
    if (!url) return this.removerTarefa(id);
    return this.http.delete(`${url}/${id}`, this.authOptions);
  }
}
