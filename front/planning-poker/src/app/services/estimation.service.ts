import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class EstimationService {
  private api = `${environment.apiUrl}/api`;

  constructor(private http: HttpClient) {}

  // Garante envio do token mesmo quando chamado fora do contexto de injeção do Angular
  private get authOptions() {
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('token') : null;
    return token ? { headers: { Authorization: `Bearer ${token}` } } : {};
  }

  votar(taskId: string, participante: string, pontos: number): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/votar`, { participante, pontos }, this.authOptions);
  }

  votarHoras(taskId: string, participante: string, horas: number): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/votarHoras`, { participante, horas }, this.authOptions);
  }

  listar(taskId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/tarefas/${taskId}/estimativas/listar`, this.authOptions);
  }

  todosVotaramPontos(taskId: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.api}/tarefas/${taskId}/estimativas/todos-votaram-pontos`, this.authOptions);
  }

  todosVotaramHoras(taskId: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.api}/tarefas/${taskId}/estimativas/todos-votaram-horas`, this.authOptions);
  }

  getResumoVotos(taskId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/tarefas/${taskId}/estimativas/resumo-votos`, this.authOptions);
  }

  revelarPontos(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/revelarPontos`, {}, this.authOptions);
  }

  revelarHoras(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/revelar-horas`, {}, this.authOptions);
  }

  resetar(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/resetar`, {}, this.authOptions);
  }
}
