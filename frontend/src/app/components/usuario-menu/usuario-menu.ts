import { ChangeDetectorRef, Component, ElementRef, HostListener, Input, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Avatar } from '../avatar/avatar';
import { PerfilDTO, PerfilService } from '../../services/perfil.service';

type Aviso = { texto: string; tipo: 'ok' | 'erro' } | null;

const NOMES_PERFIL: Record<string, string> = {
  SUPER: 'Super usuário', ADMIN: 'Moderador', JOGADOR: 'Player', OBSERVADOR: 'Observador', TESTE: 'Testador'
};

/**
 * Avatar + nome no cabeçalho das telas. Clicar abre o "Meu perfil" (foto, e-mail e senha) num diálogo, sem trocar
 * de rota: na sala, sair da tela fecharia o WebSocket (e o backend inativa a sala quando o moderador sai).
 */
@Component({
  selector: 'app-usuario-menu',
  standalone: true,
  imports: [FormsModule, Avatar],
  templateUrl: './usuario-menu.html',
  styleUrls: ['./usuario-menu.scss']
})
export class UsuarioMenu {
  @Input({ required: true }) usuario: string | null = '';
  /** Esconde o nome ao lado do avatar (cabeçalhos apertados). */
  @Input() mostrarNome = true;

  @ViewChild('arquivoFoto') arquivoFoto?: ElementRef<HTMLInputElement>;

  /** Move o diálogo para o <body>, levando o tema escuro da tela, se houver. */
  @ViewChild('camada') set camada(el: ElementRef<HTMLElement> | undefined) {
    if (!el) return;
    const escuro = !!this.host.nativeElement.closest('.tema-escuro');
    el.nativeElement.classList.toggle('tema-escuro', escuro);
    document.body.appendChild(el.nativeElement);
  }

  aberto = false;
  carregando = false;
  perfil: PerfilDTO | null = null;

  email = '';
  senhaAtual = '';
  novaSenha = '';
  confirmacao = '';

  enviandoFoto = false;
  salvandoEmail = false;
  salvandoSenha = false;

  avisoFoto: Aviso = null;
  avisoEmail: Aviso = null;
  avisoSenha: Aviso = null;

  private servico = inject(PerfilService);
  private cdr = inject(ChangeDetectorRef);
  private host = inject<ElementRef<HTMLElement>>(ElementRef);

  get nomePerfil(): string {
    return NOMES_PERFIL[this.perfil?.perfil ?? ''] ?? this.perfil?.perfil ?? '';
  }

  abrir(): void {
    this.aberto = true;
    this.carregando = true;
    this.avisoFoto = this.avisoEmail = this.avisoSenha = null;
    this.senhaAtual = this.novaSenha = this.confirmacao = '';
    this.cdr.detectChanges();
    this.servico.meuPerfil().subscribe({
      next: perfil => {
        this.perfil = perfil;
        this.email = perfil.email ?? '';
        this.carregando = false;
        this.cdr.detectChanges();
      },
      error: (err: HttpErrorResponse) => {
        this.carregando = false;
        this.avisoEmail = { texto: this.mensagem(err, 'Não foi possível carregar o seu perfil.'), tipo: 'erro' };
        this.cdr.detectChanges();
      }
    });
  }

  @HostListener('document:keydown.escape')
  fechar(): void {
    if (!this.aberto) return;
    this.aberto = false;
    this.cdr.detectChanges();
  }

  escolherFoto(): void {
    this.arquivoFoto?.nativeElement.click();
  }

  fotoEscolhida(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    input.value = ''; // escolher o mesmo arquivo de novo também dispara o change
    if (!arquivo || !this.usuario) return;
    if (arquivo.size > 15 * 1024 * 1024) {
      this.avisoFoto = { texto: 'Imagem grande demais (máximo 15 MB).', tipo: 'erro' };
      this.cdr.detectChanges();
      return;
    }
    this.enviandoFoto = true;
    this.avisoFoto = null;
    this.cdr.detectChanges();
    this.servico.enviarFoto(arquivo, this.usuario).subscribe({
      next: perfil => {
        this.perfil = perfil;
        this.enviandoFoto = false;
        this.avisoFoto = { texto: 'Foto atualizada.', tipo: 'ok' };
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.enviandoFoto = false;
        this.avisoFoto = { texto: this.mensagem(err, 'Não foi possível enviar a foto.'), tipo: 'erro' };
        this.cdr.detectChanges();
      }
    });
  }

  removerFoto(): void {
    if (!this.usuario) return;
    this.enviandoFoto = true;
    this.cdr.detectChanges();
    this.servico.removerFoto(this.usuario).subscribe({
      next: perfil => {
        this.perfil = perfil;
        this.enviandoFoto = false;
        this.avisoFoto = { texto: 'Foto removida.', tipo: 'ok' };
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.enviandoFoto = false;
        this.avisoFoto = { texto: this.mensagem(err, 'Não foi possível remover a foto.'), tipo: 'erro' };
        this.cdr.detectChanges();
      }
    });
  }

  salvarEmail(): void {
    const email = this.email.trim();
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      this.avisoEmail = { texto: 'Informe um e-mail válido.', tipo: 'erro' };
      return;
    }
    this.salvandoEmail = true;
    this.avisoEmail = null;
    this.cdr.detectChanges();
    this.servico.alterarEmail(email || null).subscribe({
      next: perfil => {
        this.perfil = perfil;
        this.email = perfil.email ?? '';
        this.salvandoEmail = false;
        this.avisoEmail = { texto: perfil.email ? 'E-mail salvo.' : 'E-mail removido.', tipo: 'ok' };
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.salvandoEmail = false;
        this.avisoEmail = { texto: this.mensagem(err, 'Não foi possível salvar o e-mail.'), tipo: 'erro' };
        this.cdr.detectChanges();
      }
    });
  }

  alterarSenha(): void {
    this.avisoSenha = null;
    if (!this.senhaAtual.trim() || !this.novaSenha.trim()) {
      this.avisoSenha = { texto: 'Informe a senha atual e a nova senha.', tipo: 'erro' };
      return;
    }
    if (this.novaSenha.trim().length < 6) {
      this.avisoSenha = { texto: 'A nova senha deve ter pelo menos 6 caracteres.', tipo: 'erro' };
      return;
    }
    if (this.novaSenha !== this.confirmacao) {
      this.avisoSenha = { texto: 'A confirmação não confere com a nova senha.', tipo: 'erro' };
      return;
    }
    this.salvandoSenha = true;
    this.cdr.detectChanges();
    this.servico.alterarSenha(this.senhaAtual, this.novaSenha).subscribe({
      next: () => {
        this.salvandoSenha = false;
        this.senhaAtual = this.novaSenha = this.confirmacao = '';
        this.avisoSenha = { texto: 'Senha alterada. Use a nova senha no próximo login.', tipo: 'ok' };
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.salvandoSenha = false;
        this.avisoSenha = { texto: this.mensagem(err, 'Não foi possível alterar a senha.'), tipo: 'erro' };
        this.cdr.detectChanges();
      }
    });
  }

  private mensagem(err: unknown, padrao: string): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) return 'Erro ao conectar com o servidor.';
      return err.error?.message || padrao;
    }
    return (err as Error)?.message || padrao;
  }
}
