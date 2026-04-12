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

  votar(taskId: string, participante: string, pontos: number): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/votar`, {
      participante,
      pontos
    });
  }

  // CORRIGIDO: campo renomeado de horaSelecionada para horas, conforme EstimativaHorasDTO no backend
  votarHoras(taskId: string, participante: string, horas: number): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/votarHoras`, {
      participante,
      horas
    });
  }

  listar(taskId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/tarefas/${taskId}/estimativas/listar`);
  }

  todosVotaramPontos(taskId: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.api}/tarefas/${taskId}/estimativas/todos-votaram-pontos`);
  }

  todosVotaramHoras(taskId: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.api}/tarefas/${taskId}/estimativas/todos-votaram-horas`);
  }

  getResumoVotos(taskId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/tarefas/${taskId}/estimativas/resumo-votos`);
  }

  revelarPontos(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/revelarPontos`, {});
  }

  revelarHoras(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/revelar-horas`, {});
  }

  resetar(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/resetar`, {});
  }
}
