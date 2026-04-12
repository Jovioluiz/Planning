import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { tap, catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private api = `${environment.apiUrl}/api/auth`;

  constructor(private http: HttpClient) {}

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
          return true;
        }
        return false;
      }),
      catchError(() => of(false))
    );
  }

  logout(): void {
    sessionStorage.removeItem('usuario');
    sessionStorage.removeItem('perfil');
    sessionStorage.removeItem('token');
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

  isLogado(): boolean {
    return !!this.getUsuario() && !!this.getToken();
  }
}
