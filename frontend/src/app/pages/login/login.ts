import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';
import { TaskService } from '../../services/task.service';
import { PerfilService } from '../../services/perfil.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterModule, CommonModule],
  templateUrl: './login.html',
  styleUrls: ['./login.scss']
})
export class Login implements OnInit {
  usuario = '';
  senha = '';
  perfil = '';
  erro = '';
  carregando = false;

  constructor(
    private auth: AuthService,
    private router: Router,
    private taskService: TaskService,
    private perfilService: PerfilService,
    private cdr: ChangeDetectorRef
  ) {}

  /** Modo "Esqueci minha senha". */
  esqueci = false;
  usuarioOuEmail = '';
  linkEnviado = false;
  /** Mensagem de sucesso no login (ex.: vindo da redefinição de senha). */
  aviso = '';

  ngOnInit(): void {
    const estado = typeof history !== 'undefined' ? history.state : null;
    if (estado?.senhaRedefinida) {
      this.usuario = String(estado.usuario ?? '').toUpperCase();
      this.aviso = 'Senha redefinida. Entre com a nova senha.';
    }
  }

  abrirEsqueci(): void {
    this.esqueci = true;
    this.linkEnviado = false;
    this.erro = '';
    this.aviso = '';
    this.usuarioOuEmail = this.usuario;
    this.cdr.detectChanges();
  }

  voltarAoLogin(): void {
    this.esqueci = false;
    this.erro = '';
    this.cdr.detectChanges();
  }

  enviarLink(event: Event): void {
    event.preventDefault();
    this.erro = '';
    const chave = this.usuarioOuEmail.trim();
    if (!chave) {
      this.erro = 'Informe o usuário ou o e-mail';
      return;
    }
    this.carregando = true;
    this.cdr.detectChanges();
    this.perfilService.esqueciSenha(chave).subscribe({
      next: () => {
        this.carregando = false;
        this.linkEnviado = true;
        this.cdr.detectChanges();
      },
      error: (err: HttpErrorResponse) => {
        this.carregando = false;
        this.erro = err.status === 0
          ? 'Erro ao conectar com o servidor'
          : err.error?.message || 'Não foi possível enviar o link';
        this.cdr.detectChanges();
      }
    });
  }

  onUsuarioChange(value: string): void {
    this.usuario = value.toUpperCase();
    this.cdr.detectChanges();
  }

  login(event: Event): void {
    event.preventDefault();
    this.erro = '';

    if (!this.usuario || !this.senha || !this.perfil) {
      this.erro = 'Preencha todos os campos';
      return;
    }

    this.carregando = true;

    this.auth.login(this.usuario, this.senha, this.perfil).subscribe({
      next: (sucesso) => {
        if (!sucesso) {
          this.erro = 'Usuário ou senha inválidos';
          this.carregando = false;
          return;
        }

        if (this.auth.isSuper()) {
          this.carregando = false;
          this.router.navigate(['/usuarios']);
          return;
        }

        if (this.auth.isAdmin()) {
          this.carregando = false;
          this.router.navigate(['/salas']);
          return;
        }

        // JOGADOR / OBSERVADOR: verifica se vem de um link de sala
        const pendingCodigo = typeof sessionStorage !== 'undefined'
          ? sessionStorage.getItem('pendingSalaCodigo')
          : null;
        if (pendingCodigo && (this.auth.isJogador() || this.auth.isObservador() || this.auth.isTeste())) {
          this.carregando = false;
          this.router.navigate(['/sala', pendingCodigo]);
          return;
        }

        if (this.auth.isJogador()) {
          this.carregando = false;
          this.router.navigate(['/aguardando']);
          return;
        }

        // OBSERVADOR / TESTE: busca tarefa liberada diretamente
        this.taskService.getTarefasLiberadas().subscribe({
          next: (tarefas) => {
            this.carregando = false;
            if (tarefas.length > 0) {
              this.router.navigate(['/estimativas', tarefas[0].id]);
            } else {
              this.router.navigate(['/aguardando']);
            }
          },
          error: () => {
            this.carregando = false;
            this.router.navigate(['/aguardando']);
          }
        });
      },
      error: (err: Error) => {
        this.erro = err.message || 'Erro ao conectar com o servidor';
        this.carregando = false;
      }
    });
  }
}
