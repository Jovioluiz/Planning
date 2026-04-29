import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { EstimationService } from '../../services/estimation.service';
import { TaskService, ITask } from '../../services/task.service';
import { WebSocketService } from '../../websocket/websocket.service';

// Sentinela local para a carta "café" (0 é enviado ao backend)
const CARTA_CAFE = -1;

@Component({
  selector: 'app-estimation-board',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './estimation-board.html',
  styleUrls: ['./estimation-board.scss']
})
export class EstimationBoard implements OnInit, OnDestroy {
  participante = '';
  taskId: string | null = null;
  erro = '';
  estimativas: any[] = [];
  cartas = [1, 2, 3, 5, 8, 13, 21, CARTA_CAFE];
  horas = [1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 20, 24];
  tarefa: any = null;
  estadoVotacao: 'pontos' | 'aguardando_horas' | 'horas' | 'finalizado' = 'pontos';
  pontoSelecionado: number | null = null;
  horaSelecionada: number = 0;
  todosVotaram = false;
  votando = false;
  foiSkipado = false;
  sessionEnded: 'finalizada' | 'pulada' | null = null;
  tempoDecorrido = '00:00';
  usuariosOnline: string[] = [];
  readonly CARTA_CAFE = CARTA_CAFE;
  private pollInterval: any = null;
  private timerInterval: any = null;
  private wasLiberated = false;

  constructor(
    private taskService: TaskService,
    private estimationService: EstimationService,
    private router: Router,
    private route: ActivatedRoute,
    private wsService: WebSocketService,
    private auth: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  get isAdmin(): boolean { return this.auth.isAdmin(); }
  get isObservador(): boolean { return this.auth.isObservador(); }

  get rodadaAtualNum(): number {
    if (this.tarefa?.rodadaAtual) return this.tarefa.rodadaAtual;
    if (this.estimativas.length === 0) return 1;
    return Math.max(...this.estimativas.map((e: any) => e.rodada ?? 1));
  }

  get estimativasRodadaAtual(): any[] {
    const rodada = this.rodadaAtualNum;
    return this.estimativas.filter((e: any) => (e.rodada ?? 1) === rodada);
  }

  get estimativasRodadaAnterior(): any[] {
    const rodada = this.rodadaAtualNum;
    return this.estimativas.filter((e: any) => (e.rodada ?? 1) < rodada);
  }

  ngOnInit(): void {
    this.participante = this.auth.getUsuario() || '';
    this.taskId = this.route.snapshot.paramMap.get('id')!;
    this.carregarTarefa();
    this.atualizarEstimativas();
    this.carregarUsuariosOnline();

    this.wsService.subscribe('/topic/sessoes', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.acao === 'USUARIO_CONECTADO' && !this.usuariosOnline.includes(data.usuario)) {
        this.usuariosOnline = [...this.usuariosOnline, data.usuario];
      } else if (data.acao === 'USUARIO_DESCONECTADO') {
        this.usuariosOnline = this.usuariosOnline.filter((u: string) => u !== data.usuario);
      }
      this.cdr.detectChanges();
    });

    this.wsService.subscribe('/topic/estimativas', (msg) => {
      const data = JSON.parse(msg.body);
      if (String(data.taskId) === String(this.taskId)) {
        if (data.acao === 'REVELAR_HORAS') {
          if (this.timerInterval) { clearInterval(this.timerInterval); this.timerInterval = null; }
          this.atualizarEstimativas();
          this.carregarTarefa();
        } else if (data.acao === 'REVELAR_PONTOS' || data.acao === 'HORAS_LIBERADAS') {
          this.atualizarEstimativas();
          this.carregarTarefa();
        } else if (data.acao === 'PARTICIPANTE_REMOVIDO') {
          if (!this.isAdmin && !this.isObservador && data.participante === this.participante) {
            this.foiSkipado = true;
            this.votando = false;
            this.cdr.detectChanges();
          }
          this.atualizarEstimativas();
          if (this.isAdmin) this.checkTodosVotaram();
        } else if (data.acao === 'NOVA_RODADA') {
          this.pontoSelecionado = null;
          this.horaSelecionada = 0;
          this.estadoVotacao = 'pontos';
          this.todosVotaram = false;
          this.foiSkipado = false;
          this.cdr.detectChanges();
          // Carrega tarefa primeiro (rodadaAtual atualizado) e só então estimativas,
          // evitando que atualizarEstimativas ache o voto da rodada anterior e reverta o estado
          this.taskService.getTaskById(this.taskId!).subscribe({
            next: (tarefa) => {
              this.tarefa = tarefa;
              this.atualizarEstimativas();
              this.cdr.detectChanges();
            },
            error: () => this.atualizarEstimativas()
          });
        } else if (data.acao === 'TAREFA_FINALIZADA') {
          this.sessionEnded = 'finalizada';
          if (this.pollInterval) { clearInterval(this.pollInterval); this.pollInterval = null; }
          this.cdr.detectChanges();
        } else if (data.acao === 'TAREFA_PULADA') {
          this.sessionEnded = 'pulada';
          if (this.pollInterval) { clearInterval(this.pollInterval); this.pollInterval = null; }
          this.cdr.detectChanges();
        }
      }
    });

    // Polling a cada 5s: verifica estado da tarefa (finalização/pulo) e atualiza votos
    this.pollInterval = setInterval(() => {
      if (!this.sessionEnded) {
        this.carregarTarefa();
        if (this.estadoVotacao !== 'finalizado') this.atualizarEstimativas();
      }
    }, 5000);
  }

  ngOnDestroy(): void {
    this.wsService.unsubscribe('/topic/estimativas');
    this.wsService.unsubscribe('/topic/sessoes');
    if (this.pollInterval) clearInterval(this.pollInterval);
    if (this.timerInterval) clearInterval(this.timerInterval);
  }

  private carregarUsuariosOnline(): void {
    this.taskService.getUsuariosOnline().subscribe({
      next: (res) => { this.usuariosOnline = res; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  irParaAguardando(): void {
    this.router.navigate(['/aguardando']);
  }

  private iniciarTimer(liberadaEm: string): void {
    if (this.timerInterval) clearInterval(this.timerInterval);
    this.timerInterval = setInterval(() => {
      const diff = Math.floor((Date.now() - new Date(liberadaEm).getTime()) / 1000);
      const mins = Math.floor(diff / 60).toString().padStart(2, '0');
      const secs = (diff % 60).toString().padStart(2, '0');
      this.tempoDecorrido = `${mins}:${secs}`;
      this.cdr.detectChanges();
    }, 1000);
  }

  carregarTarefa(): void {
    this.taskService.getTaskById(this.taskId!).subscribe({
      next: (tarefa) => {
        this.tarefa = tarefa;
        if (tarefa.liberadaEm && !tarefa.horasReveladas && !this.timerInterval) {
          this.iniciarTimer(tarefa.liberadaEm);
        }
        this.sincronizarEstado(tarefa);
        if (this.isAdmin) this.checkTodosVotaram();
        this.cdr.detectChanges();
      },
      error: () => {
        this.erro = 'Não foi possível carregar os dados da tarefa.';
        this.cdr.detectChanges();
      }
    });
  }

  private sincronizarEstado(tarefa: any): void {
    if (this.sessionEnded) return;

    // Tarefa marcada como estimada → admin finalizou
    if (tarefa.estimada) {
      this.sessionEnded = 'finalizada';
      if (this.pollInterval) { clearInterval(this.pollInterval); this.pollInterval = null; }
      this.cdr.detectChanges();
      return;
    }

    // Rastreia se a tarefa já esteve liberada nesta sessão
    if (tarefa.liberada) {
      this.wasLiberated = true;
    } else if (this.wasLiberated && !tarefa.estimada) {
      // Estava liberada, agora não está mais e não foi estimada → admin pulou
      this.sessionEnded = 'pulada';
      if (this.pollInterval) { clearInterval(this.pollInterval); this.pollInterval = null; }
      this.cdr.detectChanges();
      return;
    }

    if (tarefa.pontosRevelados && tarefa.horasReveladas) {
      this.estadoVotacao = 'finalizado';
    } else if (tarefa.pontosRevelados && tarefa.horasLiberadas) {
      // Admin liberou horas: move para votação de horas (nunca regride de 'finalizado')
      if (this.estadoVotacao === 'pontos' || this.estadoVotacao === 'aguardando_horas') {
        this.estadoVotacao = 'horas';
      }
    } else if (tarefa.pontosRevelados && !tarefa.horasLiberadas) {
      // Pontos revelados mas horas ainda não liberadas: entra em espera
      if (this.estadoVotacao === 'pontos') {
        this.estadoVotacao = 'aguardando_horas';
      }
    }
  }

  votarHoras(): void {
    if (!this.horaSelecionada) {
      this.erro = 'Selecione uma estimativa de horas!';
      this.cdr.detectChanges();
      return;
    }

    this.votando = true;
    this.erro = '';
    this.cdr.detectChanges();

    this.estimationService.votarHoras(this.taskId!, this.participante, this.horaSelecionada).subscribe({
      next: () => {
        this.estadoVotacao = 'finalizado';
        this.erro = '';
        this.atualizarEstimativas();
        this.cdr.detectChanges();
      },
      error: () => {
        this.erro = 'Erro ao registrar horas. Tente novamente.';
        this.cdr.detectChanges();
      },
      complete: () => {
        this.votando = false;
        this.cdr.detectChanges();
      }
    });
  }

  selecionarPonto(valor: number): void {
    if (this.votando) return;
    this.pontoSelecionado = valor;
    this.cdr.detectChanges();
  }

  votarPontos(): void {
    if (!this.participante.trim()) {
      this.erro = 'Informe o nome do participante!';
      this.cdr.detectChanges();
      return;
    }
    if (this.pontoSelecionado === null) {
      this.erro = 'Selecione uma carta de pontos!';
      this.cdr.detectChanges();
      return;
    }

    this.votando = true;
    this.erro = '';
    this.cdr.detectChanges();

    // Carta café → envia 0 ao backend
    const pontos = this.pontoSelecionado === CARTA_CAFE ? 0 : this.pontoSelecionado;

    this.estimationService.votar(this.taskId!, this.participante, pontos).subscribe({
      next: () => {
        this.estadoVotacao = 'aguardando_horas';
        this.erro = '';
        this.atualizarEstimativas();
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        if (err.status === 409) {
          this.erro = err.error?.message || 'Você já votou nesta tarefa.';
          this.estadoVotacao = 'aguardando_horas';
          this.atualizarEstimativas();
        } else {
          this.erro = 'Erro ao registrar voto. Tente novamente.';
        }
        this.cdr.detectChanges();
      },
      complete: () => {
        this.votando = false;
        this.cdr.detectChanges();
      }
    });
  }

  atualizarEstimativas(): void {
    this.estimationService.listar(this.taskId!).subscribe({
      next: (res) => {
        // Armazena o raw da API: { participante, pontos, horas, revealed, horasReveladas }
        this.estimativas = res;

        if (!this.isAdmin && !this.isObservador) {
          const rodadaAtual = this.tarefa?.rodadaAtual
            ?? (res.length > 0 ? Math.max(...res.map((e: any) => e.rodada ?? 1)) : 1);
          const self = res.find((e: any) => e.participante === this.participante && (e.rodada ?? 1) === rodadaAtual);
          if (self) {
            // horas === null → não votou horas; "🔒" ou valor → votou
            const votouHoras = self.horas !== null && self.horas !== undefined;
            if (!votouHoras) {
              if (this.tarefa?.horasLiberadas) {
                this.estadoVotacao = 'horas';
              } else if (this.tarefa?.pontosRevelados) {
                this.estadoVotacao = 'aguardando_horas';
              }
            } else {
              this.estadoVotacao = 'finalizado';
            }
          }
        }

        if (this.isAdmin) this.checkTodosVotaram();
        this.cdr.detectChanges();
      },
      error: () => {
        this.erro = 'Erro ao carregar estimativas.';
        this.cdr.detectChanges();
      }
    });
  }

  private readonly FIBONACCI = [1, 2, 3, 5, 8, 13, 21];

  private closestFibIndex(value: number): number {
    let closest = 0;
    let minDist = Math.abs(this.FIBONACCI[0] - value);
    for (let i = 1; i < this.FIBONACCI.length; i++) {
      const dist = Math.abs(this.FIBONACCI[i] - value);
      if (dist < minDist) { minDist = dist; closest = i; }
    }
    return closest;
  }

  private calcMedianaPontos(): number | null {
    const valores: number[] = this.estimativasRodadaAtual
      .map((e: any) => e.pontos)
      .filter((p: any) => p !== null && p !== undefined && p > 0)
      .sort((a: number, b: number) => a - b);
    if (valores.length === 0) return null;
    const mid = Math.floor(valores.length / 2);
    return valores.length % 2 !== 0 ? valores[mid] : (valores[mid - 1] + valores[mid]) / 2;
  }

  get nivelDivergencia(): 'consenso' | 'proximo' | 'divergente' | null {
    if (!this.tarefa?.pontosRevelados) return null;
    const valores: number[] = this.estimativasRodadaAtual
      .map((e: any) => e.pontos)
      .filter((p: any) => p !== null && p !== undefined && p > 0);
    if (valores.length === 0) return null;
    if (valores.every(v => v === valores[0])) return 'consenso';
    const idxMin = this.closestFibIndex(Math.min(...valores));
    const idxMax = this.closestFibIndex(Math.max(...valores));
    const diff = idxMax - idxMin;
    if (diff <= 1) return 'proximo';
    return 'divergente';
  }

  classificarVoto(pontos: number | null): 'otimista' | 'pessimista' | null {
    if (!this.tarefa?.pontosRevelados || pontos === null || pontos === undefined || pontos === 0) return null;
    const mediana = this.calcMedianaPontos();
    if (mediana === null) return null;
    const diff = this.closestFibIndex(pontos) - this.closestFibIndex(mediana);
    if (diff <= -2) return 'otimista';
    if (diff >= 2) return 'pessimista';
    return null;
  }

  checkTodosVotaram(): void {
    if (this.estadoVotacao === 'pontos') {
      this.estimationService.todosVotaramPontos(this.taskId!).subscribe({
        next: (res) => { this.todosVotaram = res; this.cdr.detectChanges(); },
        error: () => {}
      });
    } else if (this.estadoVotacao === 'horas') {
      this.estimationService.todosVotaramHoras(this.taskId!).subscribe({
        next: (res) => { this.todosVotaram = res; this.cdr.detectChanges(); },
        error: () => {}
      });
    } else {
      this.todosVotaram = false;
    }
  }

  liberarHorasVotacao(): void {
    this.taskService.liberarHorasVotacao(this.taskId!).subscribe({
      next: () => { this.carregarTarefa(); },
      error: () => {}
    });
  }

  revelar(): void {
    if (this.estadoVotacao === 'pontos') {
      this.estimationService.revelarPontos(this.taskId!).subscribe({
        next: () => { this.atualizarEstimativas(); this.carregarTarefa(); },
        error: () => {}
      });
    } else if (this.estadoVotacao === 'horas') {
      this.estimationService.revelarHoras(this.taskId!).subscribe({
        next: () => { this.atualizarEstimativas(); this.carregarTarefa(); },
        error: () => {}
      });
    }
  }

  novaRodada(): void {
    this.estimationService.novaRodada(this.taskId!).subscribe({
      next: () => {
        this.pontoSelecionado = null;
        this.horaSelecionada = 0;
        this.estadoVotacao = 'pontos';
        this.todosVotaram = false;
        this.atualizarEstimativas();
        this.carregarTarefa();
      },
      error: () => {}
    });
  }

  skipParticipante(participante: string): void {
    this.taskService.removerParticipante(this.taskId!, participante).subscribe({
      next: () => {
        this.atualizarEstimativas();
        this.checkTodosVotaram();
      },
      error: () => {}
    });
  }

  resetar(): void {
    this.estimationService.resetar(this.taskId!).subscribe({
      next: () => {
        this.pontoSelecionado = null;
        this.horaSelecionada = 0;
        this.estadoVotacao = 'pontos';
        this.todosVotaram = false;
        this.pontoSelecionado = null;
        this.horaSelecionada = 0;
        this.atualizarEstimativas();
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }
}
