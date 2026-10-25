import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API } from './api-jakarta';

/** ConfiguracaoRedmineDTO: url é o link da consulta; a chave nunca volta, só os últimos 4 caracteres. */
export interface ConfiguracaoRedmine {
  configurada: boolean;
  url: string | null;
  projeto: string | null;
  finalChave: string | null;
  atualizadaEm: string | null;
}

/** Chave em branco mantém a gravada. */
export interface SalvarConfiguracaoRedmine {
  url: string;
  projeto: string | null;
  chaveApi: string | null;
}

export interface TesteRedmine {
  usuario: string;
  projeto: string | null;
  /** Quantos chamados a consulta configurada traz hoje. */
  chamados: number;
}

/** Acesso ao Redmine do moderador logado (ADMIN). A importação fica no TaskService. */
@Injectable({ providedIn: 'root' })
export class RedmineService {

  constructor(private http: HttpClient) {}

  // Como no TaskService: garante o token mesmo fora do contexto de injeção
  private get authOptions() {
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('token') : null;
    return token ? { headers: { Authorization: `Bearer ${token}` } } : {};
  }

  buscarConfiguracao(): Observable<ConfiguracaoRedmine> {
    return this.http.get<ConfiguracaoRedmine>(`${API}/redmine/configuracao`, this.authOptions);
  }

  salvarConfiguracao(configuracao: SalvarConfiguracaoRedmine): Observable<ConfiguracaoRedmine> {
    return this.http.put<ConfiguracaoRedmine>(`${API}/redmine/configuracao`, configuracao, this.authOptions);
  }

  testarConexao(): Observable<TesteRedmine> {
    return this.http.post<TesteRedmine>(`${API}/redmine/configuracao/testar`, {}, this.authOptions);
  }
}
