import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, Observable , map} from 'rxjs';

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

  todosVotaram(taskId: string): Observable<{ todosVotaram: boolean }>{
    return forkJoin({
      pontos: this.todosVotaramPontos(taskId),
      horas: this.todosVotaramHoras(taskId)
  })  .pipe(
      map(result => ({
          todosVotaram: result.pontos && result.horas
      }))
  );
  }

  getResumoVotos(taskId: string): Observable<any[]> {
  return this.http.get<any[]>(
    `${this.api}/tarefas/${taskId}/estimativas/resumo-votos`
  );
}


  revelar(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/revelar`, {});
  }

  resetar(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/resetar`, {});
  }

  revelarHoras(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/revelar-horas`, {});
  }

  revelarPontos(taskId: string): Observable<any> {
    return this.http.post(`${this.api}/tarefas/${taskId}/estimativas/revelarPontos`, {});
  }

  todosVotaramPontos(taskId: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.api}/tarefas/${taskId}/estimativas/todos-votaram-pontos`);
  }

  todosVotaramHoras(taskId: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.api}/tarefas/${taskId}/estimativas/todos-votaram-horas`);
  }
  

}
