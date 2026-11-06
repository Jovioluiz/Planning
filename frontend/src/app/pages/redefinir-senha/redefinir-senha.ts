import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { PerfilService } from '../../services/perfil.service';

/**
 * Aberta pelo link do e-mail do "Esqueci minha senha" (/redefinir-senha?token=...). Grava a nova senha e volta
 * para o login com o usuário preenchido.
 */
@Component({
  selector: 'app-redefinir-senha',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './redefinir-senha.html',
  styleUrls: ['../login/login.scss']
})
export class RedefinirSenha implements OnInit {
  token = '';
  novaSenha = '';
  confirmacao = '';
  erro = '';
  carregando = false;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private perfil = inject(PerfilService);
  private cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token')?.trim() ?? '';
  }

  redefinir(event: Event): void {
    event.preventDefault();
    this.erro = '';
    if (this.novaSenha.trim().length < 6) {
      this.erro = 'A nova senha deve ter pelo menos 6 caracteres';
      return;
    }
    if (this.novaSenha !== this.confirmacao) {
      this.erro = 'A confirmação não confere com a nova senha';
      return;
    }
    this.carregando = true;
    this.cdr.detectChanges();
    this.perfil.redefinirSenha(this.token, this.novaSenha).subscribe({
      next: usuario => {
        this.carregando = false;
        this.router.navigate(['/login'], { state: { senhaRedefinida: true, usuario: usuario.usuario } });
      },
      error: (err: HttpErrorResponse) => {
        this.carregando = false;
        this.erro = err.status === 0
          ? 'Erro ao conectar com o servidor'
          : err.error?.message || 'Não foi possível redefinir a senha';
        this.cdr.detectChanges();
      }
    });
  }

  irParaLogin(): void {
    this.router.navigate(['/login']);
  }
}
