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
}

@Injectable({
  providedIn: 'root'
})
export class TaskService {
  private url = `${environment.apiUrl}/api/tasks`;

  constructor(private http: HttpClient) {}

  buscarTarefaAtiva(): Observable<ITask> {
    return this.http.get<ITask>(`${this.url}/ativa`);
  }

  getTaskById(id: string): Observable<ITask> {
    return this.http.get<ITask>(`${this.url}/${id}`);
  }

  liberarTarefa(id: string): Observable<any> {
    return this.http.post(`${this.url}/${id}/liberar`, {});
  }

  getTarefasLiberadas(): Observable<ITask[]> {
    return this.http.get<ITask[]>(`${this.url}/liberadas`);
  }

  getTarefasVotadas(): Observable<ITask[]> {
    return this.http.get<ITask[]>(`${this.url}/votadas`);
  }

  getTarefasFila(): Observable<ITask[]> {
    return this.http.get<ITask[]>(`${this.url}/fila`);
  }

  importarCSV(tarefas: any[]): Observable<any> {
    return this.http.post(`${this.url}/importar`, tarefas);
  }

  removerTarefa(id: string): Observable<any> {
    return this.http.delete(`${this.url}/excluirTarefa/${id}`);
  }
}
