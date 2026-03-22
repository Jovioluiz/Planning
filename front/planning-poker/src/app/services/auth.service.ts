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
    return this.http.post<{ success: boolean; message: string; perfil: string }>(
      `${this.api}/login`,
      { usuario, senha, perfil }
    ).pipe(
      map(response => {
        if (response.success) {
          this.setUsuario(usuario);
          this.setPerfil(response.perfil);
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

  isAdmin(): boolean {
    return this.getPerfil() === 'ADMIN';
  }

  isLogado(): boolean {
    return !!this.getUsuario();
  }
}
