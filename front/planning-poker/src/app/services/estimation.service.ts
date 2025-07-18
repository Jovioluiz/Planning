import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class EstimationService {
  private api = 'http://localhost:8081/api';

  constructor(private http: HttpClient) {}

  votar(taskId: string, participante: string, pontos: number | string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/votar`, {
      participante,
      pontos
    });

  }

  votarHoras(taskId: string, participante: string, horaSelecionada: number | string): Observable<any> {
      return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/votarHoras`, {
      participante,
      horaSelecionada
    });
  }

  listar(taskId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/tarefas/${taskId}/estimativas/listar`);
  }

  revelar(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/revelar`, {});
  }

  resetar(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/resetar`, {});
  }
}
