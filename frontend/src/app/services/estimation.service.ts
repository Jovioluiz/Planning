import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map } from 'rxjs/operators';
import { API, VotacaoDTO, paraEstimativas, paraEstimativasTeste } from './api-jakarta';

/**
 * Votação da tarefa na sala ativa. Todas as leituras saem do mesmo GET .../votacao (que já traz votos, quem
 * falta votar e a rodada anterior); as escritas vão para os endpoints de cada fase. O usuário que vota é o do
 * token: o parâmetro "participante" ficou só para não mexer nos componentes.
 */
@Injectable({
  providedIn: 'root'
})
export class EstimationService {

  constructor(private http: HttpClient) {}

  // Garante envio do token mesmo quando chamado fora do contexto de injeção do Angular
  private get authOptions() {
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('token') : null;
    return token ? { headers: { Authorization: `Bearer ${token}` } } : {};
  }

  private votacao(taskId: string, acao = ''): string {
    const salaId = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('salaId') : null;
    return `${API}/salas/${salaId}/tarefas/${taskId}/votacao${acao}`;
  }

  private consultar(taskId: string): Observable<VotacaoDTO> {
    if (typeof sessionStorage !== 'undefined' && !sessionStorage.getItem('salaId')) {
      return throwError(() => new Error('Sem sala ativa'));
    }
    return this.http.get<VotacaoDTO>(this.votacao(taskId), this.authOptions);
  }

  private agir(taskId: string, acao: string, corpo: object = {}): Observable<any> {
    return this.http.post(this.votacao(taskId, acao), corpo, this.authOptions);
  }

  votar(taskId: string, _participante: string, pontos: number): Observable<any> {
    return this.agir(taskId, '/pontos', { pontos });
  }

  votarHoras(taskId: string, _participante: string, horas: number): Observable<any> {
    return this.agir(taskId, '/horas', { horas });
  }

  votarHorasTeste(taskId: string, _participante: string, horas: number): Observable<any> {
    return this.agir(taskId, '/horas-teste', { horas });
  }

  /** Rodada atual e a anterior (para moderador/observador/testador), no formato do /estimativas/listar antigo. */
  listar(taskId: string): Observable<any[]> {
    return this.consultar(taskId).pipe(map(paraEstimativas));
  }

  getResumoVotos(taskId: string): Observable<any[]> {
    return this.listar(taskId);
  }

  listarTeste(taskId: string): Observable<any[]> {
    return this.consultar(taskId).pipe(map(paraEstimativasTeste));
  }

  /** Calculado pelo backend: participantes online da tarefa que ainda não votaram na fase aberta. */
  todosVotaramPontos(taskId: string): Observable<boolean> {
    return this.consultar(taskId).pipe(map(v => v.fase === 'PONTOS' && v.todosVotaram === true));
  }

  todosVotaramHoras(taskId: string): Observable<boolean> {
    return this.consultar(taskId).pipe(map(v => v.fase === 'HORAS' && v.todosVotaram === true));
  }

  todosTestadoresVotaram(taskId: string): Observable<boolean> {
    return this.consultar(taskId).pipe(map(v => v.todosTestadoresVotaram === true));
  }

  revelarPontos(taskId: string): Observable<any> {
    return this.agir(taskId, '/revelar-pontos');
  }

  revelarHoras(taskId: string): Observable<any> {
    return this.agir(taskId, '/revelar-horas');
  }

  revelarHorasTeste(taskId: string): Observable<any> {
    return this.agir(taskId, '/revelar-horas-teste');
  }

  novaRodada(taskId: string): Observable<any> {
    return this.agir(taskId, '/nova-rodada');
  }

  /** O "resetar" do original apagava os votos; aqui vira uma nova rodada (os votos ficam no histórico). */
  resetar(taskId: string): Observable<any> {
    return this.novaRodada(taskId);
  }
}
