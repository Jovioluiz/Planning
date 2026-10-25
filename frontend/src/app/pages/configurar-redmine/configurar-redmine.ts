import { ChangeDetectorRef, Component, OnInit, PLATFORM_ID, inject } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ConfiguracaoRedmine, RedmineService } from '../../services/redmine.service';
import { mensagemDeErro } from '../../services/api-jakarta';

/** Acesso ao Redmine do moderador: link da consulta, projeto e chave da API, usados na importação de chamados. */
@Component({
  selector: 'app-configurar-redmine',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './configurar-redmine.html',
  styleUrls: ['./configurar-redmine.scss']
})
export class ConfigurarRedmine implements OnInit {
  url = '';
  projeto = '';
  chaveApi = '';
  configuracao: ConfiguracaoRedmine | null = null;

  carregando = true;
  salvando = false;
  testando = false;
  mensagem = '';
  mensagemTipo: 'sucesso' | 'erro' | '' = '';
  temaEscuro = typeof localStorage !== 'undefined' && localStorage.getItem('tema') === 'escuro';

  private platformId = inject(PLATFORM_ID);

  constructor(
    private redmineService: RedmineService,
    readonly auth: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.redmineService.buscarConfiguracao().subscribe({
      next: (configuracao) => {
        this.aplicar(configuracao);
        this.carregando = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.carregando = false;
        this.exibir(mensagemDeErro(err?.error) ?? 'Não foi possível carregar a configuração.', 'erro');
      }
    });
  }

  salvar(): void {
    if (!this.url.trim()) {
      this.exibir('Informe o link da consulta do Redmine.', 'erro');
      return;
    }
    if (!this.configuracao?.configurada && !this.chaveApi.trim()) {
      this.exibir('Informe a chave da API.', 'erro');
      return;
    }
    this.salvando = true;
    this.cdr.detectChanges();
    this.redmineService.salvarConfiguracao({
      url: this.url.trim(),
      projeto: this.projeto.trim() || null,
      chaveApi: this.chaveApi.trim() || null
    }).subscribe({
      next: (configuracao) => {
        this.aplicar(configuracao);
        this.chaveApi = '';
        this.salvando = false;
        this.exibir('Configuração salva.', 'sucesso');
      },
      error: (err) => {
        this.salvando = false;
        this.exibir(mensagemDeErro(err?.error) ?? 'Não foi possível salvar a configuração.', 'erro');
      }
    });
  }

  testar(): void {
    this.testando = true;
    this.mensagem = '';
    this.cdr.detectChanges();
    this.redmineService.testarConexao().subscribe({
      next: (teste) => {
        this.testando = false;
        this.exibir(`Conectado como ${teste.usuario}` + (teste.projeto ? ` · projeto ${teste.projeto}` : '')
          + ` · ${teste.chamados} chamado(s) na consulta`, 'sucesso');
      },
      error: (err) => {
        this.testando = false;
        this.exibir(mensagemDeErro(err?.error) ?? 'Não foi possível conectar ao Redmine.', 'erro');
      }
    });
  }

  voltar(): void {
    const temSala = typeof sessionStorage !== 'undefined' && !!sessionStorage.getItem('salaId');
    this.router.navigate([temSala ? '/importar' : '/salas']);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  private aplicar(configuracao: ConfiguracaoRedmine): void {
    this.configuracao = configuracao;
    this.url = configuracao.url ?? '';
    this.projeto = configuracao.projeto ?? '';
  }

  private exibir(mensagem: string, tipo: 'sucesso' | 'erro'): void {
    this.mensagem = mensagem;
    this.mensagemTipo = tipo;
    this.cdr.detectChanges();
  }
}
