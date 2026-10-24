import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from './api-jakarta';

/** "Minhas salas" do moderador (SalaModeradaDTO no backend). */
export interface SalaDTO {
  id: number;
  nome: string;
  codigo: string;
  ativa: boolean;
  criadaEm: string;
  quantidadeTarefas?: number;
  tarefasPendentes?: number;
  membros: string[];
}

@Injectable({ providedIn: 'root' })
export class SalaService {
  private api = `${API}/salas`;

  constructor(private http: HttpClient) {}

  private get authOptions() {
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('token') : null;
    return token ? { headers: { Authorization: `Bearer ${token}` } } : {};
  }

  criarSala(nome: string): Observable<SalaDTO> {
    return this.http.post<SalaDTO>(this.api, { nome }, this.authOptions);
  }

  entrarNaSala(codigo: string): Observable<SalaDTO> {
    return this.http.post<SalaDTO>(`${this.api}/codigo/${encodeURIComponent(codigo)}/entrar`, {}, this.authOptions);
  }

  minhasSalas(): Observable<SalaDTO[]> {
    return this.http.get<SalaDTO[]>(`${this.api}/minhas`, this.authOptions);
  }

  /** O original reativava sozinho as salas com tarefas pendentes; aqui o moderador reativa pela tela de salas. */
  reativarSala(id: number): Observable<SalaDTO> {
    return this.http.post<SalaDTO>(`${this.api}/${id}/reativar`, {}, this.authOptions);
  }

  desativarSala(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`, this.authOptions);
  }

  getSalaByCodigo(codigo: string): Observable<any> {
    return this.http.get(`${this.api}/codigo/${encodeURIComponent(codigo)}`);
  }

  getSalaId(): string | null {
    return typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('salaId') : null;
  }

  getSalaCodigo(): string | null {
    return typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('salaCodigo') : null;
  }

  getSalaNome(): string | null {
    return typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('salaNome') : null;
  }

  setSalaContext(id: string, codigo: string, nome: string): void {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem('salaId', id);
      sessionStorage.setItem('salaCodigo', codigo);
      sessionStorage.setItem('salaNome', nome);
    }
  }

  clearSalaContext(): void {
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.removeItem('salaId');
      sessionStorage.removeItem('salaCodigo');
      sessionStorage.removeItem('salaNome');
      sessionStorage.removeItem('pendingSalaCodigo');
    }
  }

  getLinkCompartilhavel(codigo: string): string {
    if (typeof window !== 'undefined') {
      return `${window.location.origin}/#/sala/${codigo}`;
    }
    return `/#/sala/${codigo}`;
  }
}
