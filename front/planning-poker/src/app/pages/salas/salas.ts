import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { SalaService, SalaDTO } from '../../services/sala.service';

@Component({
  selector: 'app-salas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './salas.html',
  styleUrls: ['./salas.scss']
})
export class Salas implements OnInit {
  salas: SalaDTO[] = [];
  novaNome = '';
  criando = false;
  carregando = true;
  erro = '';
  linkCopiado: string | null = null;

  constructor(
    private salaService: SalaService,
    readonly auth: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.carregarSalas();
  }

  carregarSalas(): void {
    this.carregando = true;
    this.salaService.minhasSalas().subscribe({
      next: (salas) => {
        this.salas = salas;
        this.carregando = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.carregando = false;
        this.cdr.detectChanges();
      }
    });
  }

  criarSala(): void {
    if (!this.novaNome.trim()) return;
    this.criando = true;
    this.erro = '';
    this.salaService.criarSala(this.novaNome.trim()).subscribe({
      next: () => {
        this.novaNome = '';
        this.criando = false;
        this.carregarSalas();
      },
      error: (e) => {
        this.erro = e?.error?.message || 'Erro ao criar sala';
        this.criando = false;
        this.cdr.detectChanges();
      }
    });
  }

  abrirSala(sala: SalaDTO): void {
    this.salaService.setSalaContext(sala.id.toString(), sala.codigo, sala.nome);
    this.router.navigate(['/importar']);
  }

  getLinkCompartilhavel(codigo: string): string {
    return this.salaService.getLinkCompartilhavel(codigo);
  }

  copiarLink(codigo: string): void {
    const link = this.getLinkCompartilhavel(codigo);
    navigator.clipboard.writeText(link).then(() => {
      this.linkCopiado = codigo;
      this.cdr.detectChanges();
      setTimeout(() => {
        this.linkCopiado = null;
        this.cdr.detectChanges();
      }, 2000);
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
