import { ChangeDetectorRef, Component, OnInit, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser, CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../environments/environment';

interface UsuarioInfo {
  usuario: string;
  perfil: string;
}

@Component({
  selector: 'app-gerenciar-usuarios',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './gerenciar-usuarios.html',
  styleUrls: ['./gerenciar-usuarios.scss']
})
export class GerenciarUsuarios implements OnInit {
  usuarios: UsuarioInfo[] = [];
  carregando = true;
  erro = '';
  confirmando: string | null = null;
  excluindo = false;

  private platformId = inject(PLATFORM_ID);

  readonly perfilLabel: Record<string, string> = {
    ADMIN:      'Moderador',
    JOGADOR:    'Player',
    OBSERVADOR: 'Observador',
  };

  constructor(
    private http: HttpClient,
    private auth: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    if (!isPlatformBrowser(this.platformId)) return;

    this.carregando = true;
    this.erro = '';
    this.cdr.detectChanges();

    const token = this.auth.getToken();
    const headers = new HttpHeaders(token ? { Authorization: `Bearer ${token}` } : {});

    this.http.get<UsuarioInfo[]>(`${environment.apiUrl}/api/auth/usuarios`, { headers }).subscribe({
      next: (lista) => {
        this.usuarios = lista;
        this.carregando = false;
        this.cdr.detectChanges();
      },
      error: (err: HttpErrorResponse) => {
        const msg = err?.error?.message ?? err?.message ?? 'desconhecido';
        this.erro = `Erro ${err.status}: ${msg}`;
        this.carregando = false;
        this.cdr.detectChanges();
      }
    });
  }

  confirmarExclusao(usuario: string): void {
    this.confirmando = usuario;
    this.cdr.detectChanges();
  }

  cancelar(): void {
    this.confirmando = null;
    this.cdr.detectChanges();
  }

  excluir(usuario: string): void {
    this.excluindo = true;
    this.cdr.detectChanges();

    const token = this.auth.getToken();
    const headers = new HttpHeaders(token ? { Authorization: `Bearer ${token}` } : {});

    this.http.delete(`${environment.apiUrl}/api/auth/usuarios/${encodeURIComponent(usuario)}`, { headers }).subscribe({
      next: () => {
        this.usuarios = this.usuarios.filter(u => u.usuario !== usuario);
        this.confirmando = null;
        this.excluindo = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.erro = 'Erro ao excluir usuário.';
        this.confirmando = null;
        this.excluindo = false;
        this.cdr.detectChanges();
      }
    });
  }

  get superUsuario(): string {
    return this.auth.getUsuario() ?? '';
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  get porPerfil(): Record<string, UsuarioInfo[]> {
    const grupos: Record<string, UsuarioInfo[]> = { ADMIN: [], JOGADOR: [], OBSERVADOR: [] };
    for (const u of this.usuarios) {
      if (grupos[u.perfil]) grupos[u.perfil].push(u);
    }
    return grupos;
  }
}
