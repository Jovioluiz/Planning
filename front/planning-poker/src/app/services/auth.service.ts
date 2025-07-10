import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable, of } from 'rxjs';
import { tap, catchError, map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private api = 'http://localhost:8081/api/auth';

  constructor(private http: HttpClient) {}

login(usuario: string, senha: string, perfil: string): Observable<boolean> {
  return this.http.post<{ success: boolean, message: string, perfil: string}>(`${this.api}/login`, { usuario, senha, perfil }).pipe(
    map(response => {
      if (response.success) {
        this.setUsuario(usuario);
        this.setPerfil(response.perfil);
        return true;
      } else {
        return false;
      }
    }),
    catchError((e) => of(false))
  );
}

  logout() {
    localStorage.removeItem('usuario');
  }

  getUsuario(): string | null {
    return localStorage.getItem('usuario');
  }

  setUsuario(usuario: string) {
    localStorage.setItem('usuario', usuario);
  }

  setPerfil(perfil: string){
    localStorage.setItem('perfil', perfil)
  }

  getPerfil(): string | null{
    return localStorage.getItem('perfil');
  }

  isLogado(): boolean {
    return !!this.getUsuario();
  }
}
