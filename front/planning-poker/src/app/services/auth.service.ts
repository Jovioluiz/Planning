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
    // Adicionado 'token?: string' no tipo de retorno esperado
    return this.http.post<{ success: boolean; message: string; perfil: string; token?: string }>(
      `${this.api}/login`,
      { usuario, senha, perfil }
    ).pipe(
      map(response => {
        // Agora verificamos o success e salvamos o token
        if (response.success && response.token) {
          this.setUsuario(usuario);
          this.setPerfil(response.perfil);
          this.setToken(response.token); // Salva o token
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
    sessionStorage.removeItem('token'); // Remove o token ao sair
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

  // --- NOVOS MÉTODOS PARA O TOKEN ---
  getToken(): string | null {
    return sessionStorage.getItem('token');
  }

  setToken(token: string): void {
    sessionStorage.setItem('token', token);
  }

  isAdmin(): boolean {
    return this.getPerfil() === 'ADMIN';
  }

  isLogado(): boolean {
    // Agora verifica também se possui o token
    return !!this.getUsuario() && !!this.getToken();
  }
}