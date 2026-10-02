import { ChangeDetectorRef, Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import {
  Estatisticas as EstatisticasData,
  EstatisticaTarefa,
  EstatisticaUsuario,
  EstatisticasService,
  FiltroEstatisticas
} from '../../services/estatisticas.service';

type Aba = 'geral' | 'sprints' | 'usuarios' | 'tarefas';

/** Tarefa com os valores exibidos: o final do moderador ou, em tarefas antigas, o derivado dos votos. */
type TarefaLinha = EstatisticaTarefa & {
  pontos: number | null;
  horas: number | null;
  valorFinalDefinido: boolean;
};

interface Barra {
  rotulo: string;
  valor: number;
  pct: number;
}

@Component({
  selector: 'app-estatisticas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './estatisticas.html',
  styleUrls: ['./estatisticas.scss']
})
export class Estatisticas implements OnInit {
  dados: EstatisticasData | null = null;
  carregando = true;
  erro = '';
  aba: Aba = 'geral';

  filtro: FiltroEstatisticas = { salaId: null, sprint: null, de: null, ate: null };
  buscaTarefa = '';

  ordemUsuarios: { campo: keyof EstatisticaUsuario; asc: boolean } = { campo: 'votosPontos', asc: false };
  ordemTarefas: { campo: keyof TarefaLinha; asc: boolean } = { campo: 'estimadaEm', asc: false };
  private linhasTarefas: TarefaLinha[] = [];

  barrasPontos: Barra[] = [];
  barrasHoras: Barra[] = [];
  barrasSprints: Barra[] = [];

  constructor(
    private estatisticasService: EstatisticasService,
    readonly auth: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef,
    @Inject(PLATFORM_ID) private platformId: object
  ) {}

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    if (!this.auth.isAdmin() && !this.auth.isSuper()) {
      this.router.navigate(['/login']);
      return;
    }
    const salaId = Number(this.route.snapshot.queryParamMap.get('salaId'));
    if (Number.isFinite(salaId) && salaId > 0) this.filtro.salaId = salaId;
    this.carregar();
  }

  carregar(): void {
    this.carregando = true;
    this.erro = '';
    this.cdr.detectChanges();
    this.estatisticasService.buscar(this.filtro).subscribe({
      next: (dados) => {
        this.dados = dados;
        // Sprint filtrada pode não existir mais após trocar de sala
        if (this.filtro.sprint && !dados.sprints.includes(this.filtro.sprint)) {
          this.filtro.sprint = null;
        }
        this.montarGraficos(dados);
        this.linhasTarefas = dados.tarefas.map(t => ({
          ...t,
          pontos: t.pontosFinais ?? t.modaPontos,
          horas: t.horasFinais ?? t.mediaHoras,
          valorFinalDefinido: t.pontosFinais !== null
        }));
        this.carregando = false;
        this.cdr.detectChanges();
      },
      error: (e) => {
        this.erro = e?.error?.message || 'Erro ao carregar estatísticas';
        this.carregando = false;
        this.cdr.detectChanges();
      }
    });
  }

  limparFiltros(): void {
    this.filtro = { salaId: null, sprint: null, de: null, ate: null };
    this.carregar();
  }

  get temFiltro(): boolean {
    return Object.values(this.filtro).some(v => v !== null && v !== '');
  }

  private montarGraficos(d: EstatisticasData): void {
    this.barrasPontos = this.barras(
      Object.entries(d.distribuicaoPontos).map(([k, v]) => [k === '0' ? '☕' : k, v])
    );
    this.barrasHoras = this.barras(
      Object.entries(d.distribuicaoHoras).map(([k, v]) => [`${Number(k)}h`, v])
    );
    this.barrasSprints = this.barras(
      d.porSprint.map(s => [s.sprint, s.totalPontos ?? 0])
    );
  }

  private barras(entradas: [string, number][]): Barra[] {
    const max = Math.max(1, ...entradas.map(([, v]) => v));
    return entradas.map(([rotulo, valor]) => ({ rotulo, valor, pct: (valor / max) * 100 }));
  }

  get usuariosOrdenados(): EstatisticaUsuario[] {
    return this.ordenar(this.dados?.porUsuario ?? [], this.ordemUsuarios);
  }

  get tarefasFiltradas(): TarefaLinha[] {
    const termo = this.buscaTarefa.trim().toLowerCase();
    const lista = this.linhasTarefas.filter(t =>
      !termo || String(t.numero).includes(termo) || (t.titulo ?? '').toLowerCase().includes(termo)
    );
    return this.ordenar(lista, this.ordemTarefas);
  }

  ordenarUsuarios(campo: keyof EstatisticaUsuario): void {
    this.ordemUsuarios = this.alternar(this.ordemUsuarios, campo);
  }

  ordenarTarefas(campo: keyof TarefaLinha): void {
    this.ordemTarefas = this.alternar(this.ordemTarefas, campo);
  }

  seta(ordem: { campo: string; asc: boolean }, campo: string): string {
    return ordem.campo === campo ? (ordem.asc ? '▲' : '▼') : '';
  }

  private alternar<T>(atual: { campo: T; asc: boolean }, campo: T): { campo: T; asc: boolean } {
    return atual.campo === campo ? { campo, asc: !atual.asc } : { campo, asc: false };
  }

  private ordenar<T>(lista: T[], ordem: { campo: keyof T; asc: boolean }): T[] {
    const dir = ordem.asc ? 1 : -1;
    return [...lista].sort((a, b) => {
      const va = a[ordem.campo] as unknown;
      const vb = b[ordem.campo] as unknown;
      // Valores nulos sempre no fim
      if (va === null || va === undefined) return 1;
      if (vb === null || vb === undefined) return -1;
      if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * dir;
      return String(va).localeCompare(String(vb), 'pt-BR', { numeric: true }) * dir;
    });
  }

  tendencia(desvio: number | null): string {
    if (desvio === null) return '';
    if (desvio > 0.5) return 'acima';
    if (desvio < -0.5) return 'abaixo';
    return 'alinhado';
  }

  num(valor: number | null | undefined, casas = 1): string {
    if (valor === null || valor === undefined) return '—';
    return valor.toLocaleString('pt-BR', { maximumFractionDigits: casas });
  }

  formatarDuracao(minutos: number | null): string {
    if (minutos === null) return '—';
    if (minutos < 1) return `${Math.round(minutos * 60)}s`;
    if (minutos < 60) return `${Math.round(minutos)} min`;
    const h = Math.floor(minutos / 60);
    const m = Math.round(minutos % 60);
    return m ? `${h}h ${m}min` : `${h}h`;
  }

  voltar(): void {
    // Aberta a partir da tela de uma sala (importar) → volta para ela
    if (this.route.snapshot.queryParamMap.has('salaId') && this.auth.isAdmin()) {
      this.router.navigate(['/importar']);
      return;
    }
    this.router.navigate([this.auth.isSuper() ? '/usuarios' : '/salas']);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
