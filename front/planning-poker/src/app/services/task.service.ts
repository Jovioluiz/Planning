import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { RouterModule, Router } from '@angular/router';

@Injectable({
  providedIn: 'root'
})
export class TaskService {
  url = 'http://localhost:8081/api/tasks';

  constructor(private http: HttpClient, private router: Router) { }

  buscarTarefaAtiva() {
    return this.http.get<any>(this.url + '/ativa', {});
}

getTaskById(id: string) {
  return this.http.get<any>(this.url + `/${id}`);
}

liberarTarefa(id: string) {
  return this.http.post(this.url + `/${id}/liberar`, {});
}

getTarefasLiberadas() {
  return this.http.get<ITask[]>(this.url +'/liberadas');
}

getTarefasVotadas() {
  return this.http.get<Task[]>(this.url +'/votadas');
}

getTarefasFila() {
  return this.http.get<Task[]>(this.url + '/fila');
}

importarTarefas(arquivo: FormData) {
  return this.http.post(`${this.url}/importar`, arquivo);
}

importarCSV(tarefas: any[]) {
  return this.http.post(`${this.url}/importar`, tarefas );
}

removerTarefa(id: string){
  return this.http.delete(this.url + `/excluirTarefa/${id}`, {});
}


}
