import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { WebSocketService } from '../websocket/websocket.service';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private api = `${environment.apiUrl}/api/auth`;

  constructor(private http: HttpClient, private ws: WebSocketService) {}

  login(usuario: string, senha: string, perfil: string): Observable<boolean> {
    return this.http.post<{ success: boolean; message: string; perfil: string; token?: string }>(
      `${this.api}/login`,
      { usuario, senha, perfil }
    ).pipe(
      map(response => {
        if (response.success && response.token) {
          this.setUsuario(usuario);
          this.setPerfil(response.perfil);
          this.setToken(response.token);
          this.ws.connect();
          return true;
        }
        return false;
      }),
      catchError((err) => {
        const message = err?.error?.message || 'Erro ao conectar com o servidor';
        return throwError(() => new Error(message));
      })
    );
  }

  selecionarSprint(sprint: string): Observable<any> {
    const token = this.getToken() ?? '';
    return this.http.post(`${this.api}/selecionar-sprint`, { sprint }, {
      headers: { Authorization: `Bearer ${token}` }
    }).pipe(
      tap(() => sessionStorage.setItem('sprint', sprint))
    );
  }

  getSprint(): string | null {
    return sessionStorage.getItem('sprint');
  }

  logout(): void {
    this.ws.disconnect();
    sessionStorage.removeItem('usuario');
    sessionStorage.removeItem('perfil');
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('sprint');
  }

  getUsuario(): string | null {
    return sessionStorage.getItem('usuario');
  }

  setUsuario(usuario: string): void {
    sessionStorage.setItem('usuario', usuario);
  }

  setPerfil(perfil: string): void {
    sessionStorage.setItem('perfil', perfil);
  }

  getPerfil(): string | null {
    return sessionStorage.getItem('perfil');
  }

  getToken(): string | null {
    return sessionStorage.getItem('token');
  }

  setToken(token: string): void {
    sessionStorage.setItem('token', token);
  }

  isAdmin(): boolean {
    return this.getPerfil() === 'ADMIN';
  }

  isJogador(): boolean {
    return this.getPerfil() === 'JOGADOR';
  }

  isObservador(): boolean {
    return this.getPerfil() === 'OBSERVADOR';
  }

  isSuper(): boolean {
    return this.getPerfil() === 'SUPER';
  }

  isLogado(): boolean {
    return !!this.getUsuario() && !!this.getToken();
  }
}
