import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { SalaService } from '../../services/sala.service';

@Component({
  selector: 'app-sala-landing',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sala-landing.html',
  styleUrls: ['./sala-landing.scss']
})
export class SalaLanding implements OnInit {
  codigo = '';
  salaNome = '';
  salaAtiva = false;
  carregando = true;
  entrando = false;
  erro = '';

  usuario = '';
  senha = '';
  perfil = 'JOGADOR';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    readonly auth: AuthService,
    private salaService: SalaService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.codigo = this.route.snapshot.paramMap.get('codigo') ?? '';

    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.setItem('pendingSalaCodigo', this.codigo);
    }

    this.salaService.getSalaByCodigo(this.codigo).subscribe({
      next: (sala) => {
        this.salaNome = sala.nome;
        this.salaAtiva = sala.ativa;
        this.carregando = false;
        if (this.auth.isLogado()) {
          this.joinSala();
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.erro = 'Sala não encontrada ou inativa.';
        this.carregando = false;
        this.cdr.detectChanges();
      }
    });
  }

  get jaLogado(): boolean {
    return this.auth.isLogado();
  }

  onUsuarioChange(value: string): void {
    this.usuario = value.toUpperCase();
    this.cdr.detectChanges();
  }

  loginEJuntar(event: Event): void {
    event.preventDefault();
    if (!this.usuario || !this.senha || !this.perfil) {
      this.erro = 'Preencha todos os campos';
      this.cdr.detectChanges();
      return;
    }
    this.entrando = true;
    this.erro = '';
    this.auth.login(this.usuario, this.senha, this.perfil).subscribe({
      next: (ok) => {
        if (ok) {
          this.joinSala();
        } else {
          this.erro = 'Usuário ou senha inválidos';
          this.entrando = false;
          this.cdr.detectChanges();
        }
      },
      error: (e) => {
        this.erro = e.message || 'Erro ao conectar com o servidor';
        this.entrando = false;
        this.cdr.detectChanges();
      }
    });
  }

  private joinSala(): void {
    this.entrando = true;
    this.salaService.entrarNaSala(this.codigo).subscribe({
      next: (sala) => {
        this.salaService.setSalaContext(sala.id.toString(), sala.codigo, sala.nome);
        if (typeof sessionStorage !== 'undefined') {
          sessionStorage.removeItem('pendingSalaCodigo');
        }
        this.entrando = false;
        this.cdr.detectChanges();
        this.router.navigate(['/aguardando']);
      },
      error: (e) => {
        this.erro = e?.error?.message || 'Erro ao entrar na sala';
        this.entrando = false;
        this.cdr.detectChanges();
      }
    });
  }
}
