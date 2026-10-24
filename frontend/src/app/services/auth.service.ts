import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { WebSocketService } from '../websocket/websocket.service';
import { API } from './api-jakarta';

interface TokenDTO {
  token: string;
  usuario: { usuario: string; perfil: string };
}

/** Resposta genérica do backend para usuário inexistente OU senha errada (ele não diz qual dos dois). */
const CREDENCIAIS_INVALIDAS = 'Usuário ou senha inválidos';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private api = `${API}/auth`;

  constructor(private http: HttpClient, private ws: WebSocketService) {}

  /**
   * Como no Planning original, o login também cadastra: se o usuário não existe, ele é criado com o perfil
   * escolhido. O backend novo separa as duas coisas, então o front tenta o login e, se as credenciais não
   * conferem, tenta o cadastro. Se o cadastro disser que o usuário já existe, era senha errada.
   *
   * Usuário em maiúsculas e senha sem espaços nas pontas, como o backend antigo fazia (os usuários e senhas
   * migrados foram gravados assim).
   */
  login(usuario: string, senha: string, perfil: string): Observable<boolean> {
    const credenciais = { usuario: usuario.trim().toUpperCase(), senha: senha.trim() };
    return this.entrar(credenciais).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 401 && err.error?.message === CREDENCIAIS_INVALIDAS) {
          return this.http.post(`${this.api}/registrar`, { ...credenciais, perfil }).pipe(
            switchMap(() => this.entrar(credenciais)),
            catchError((erroCadastro: HttpErrorResponse) => {
              const jaExiste = erroCadastro.status === 422 && /já existe/i.test(erroCadastro.error?.message ?? '');
              return throwError(() => new Error(jaExiste ? 'Senha incorreta.' : this.mensagem(erroCadastro)));
            })
          );
        }
        return throwError(() => new Error(this.mensagem(err)));
      })
    );
  }

  private entrar(credenciais: { usuario: string; senha: string }): Observable<boolean> {
    return this.http.post<TokenDTO>(`${this.api}/login`, credenciais).pipe(
      map(resposta => {
        this.setUsuario(resposta.usuario.usuario);
        this.setPerfil(resposta.usuario.perfil);
        this.setToken(resposta.token);
        return true;
      })
    );
  }

  private mensagem(err: HttpErrorResponse): string {
    return err?.error?.message || (err?.status === 0 ? 'Erro ao conectar com o servidor' : 'Erro ao entrar');
  }

  logout(): void {
    this.ws.disconnect();
    sessionStorage.removeItem('usuario');
    sessionStorage.removeItem('perfil');
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('salaId');
    sessionStorage.removeItem('salaCodigo');
    sessionStorage.removeItem('salaNome');
    sessionStorage.removeItem('pendingSalaCodigo');
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

  isTeste(): boolean {
    return this.getPerfil() === 'TESTE';
  }

  isSuper(): boolean {
    return this.getPerfil() === 'SUPER';
  }

  isLogado(): boolean {
    return !!this.getUsuario() && !!this.getToken();
  }
}
