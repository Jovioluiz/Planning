import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SalaDTO {
  id: number;
  nome: string;
  codigo: string;
  moderador: string;
  ativa: boolean;
  criadaEm: string;
  membros: string[];
}

@Injectable({ providedIn: 'root' })
export class SalaService {
  private api = `${environment.apiUrl}/api/salas`;

  constructor(private http: HttpClient) {}

  private get authOptions() {
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('token') : null;
    return token ? { headers: { Authorization: `Bearer ${token}` } } : {};
  }

  criarSala(nome: string): Observable<SalaDTO> {
    return this.http.post<SalaDTO>(this.api, { nome }, this.authOptions);
  }

  entrarNaSala(codigo: string): Observable<SalaDTO> {
    return this.http.post<SalaDTO>(`${this.api}/${codigo}/entrar`, {}, this.authOptions);
  }

  minhasSalas(): Observable<SalaDTO[]> {
    return this.http.get<SalaDTO[]>(`${this.api}/minhas`, this.authOptions);
  }

  getSalaByCodigo(codigo: string): Observable<any> {
    return this.http.get(`${this.api}/codigo/${codigo}`);
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
