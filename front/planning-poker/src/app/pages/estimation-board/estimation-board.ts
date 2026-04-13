import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { EstimationService } from '../../services/estimation.service';
import { TaskService } from '../../services/task.service';
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
  estadoVotacao: 'pontos' | 'horas' | 'finalizado' = 'pontos';
  pontoSelecionado: number | null = null;
  horaSelecionada: number = 0;
  todosVotaram = false;
  votando = false;
  sessionEnded: 'finalizada' | 'pulada' | null = null;
  readonly CARTA_CAFE = CARTA_CAFE;
  private pollInterval: any = null;
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

  ngOnInit(): void {
    this.participante = this.auth.getUsuario() || '';
    this.taskId = this.route.snapshot.paramMap.get('id')!;
    this.carregarTarefa();
    this.atualizarEstimativas();

    this.wsService.subscribe('/topic/estimativas', (msg) => {
      const data = JSON.parse(msg.body);
      if (String(data.taskId) === String(this.taskId)) {
        if (data.acao === 'REVELAR_PONTOS' || data.acao === 'REVELAR_HORAS') {
          this.atualizarEstimativas();
          this.carregarTarefa();
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
    if (this.pollInterval) clearInterval(this.pollInterval);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  irParaAguardando(): void {
    this.router.navigate(['/aguardando']);
  }

  carregarTarefa(): void {
    this.taskService.getTaskById(this.taskId!).subscribe({
      next: (tarefa) => {
        this.tarefa = tarefa;
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
    } else if (tarefa.pontosRevelados) {
      // Avança para horas apenas se ainda estiver em pontos; não regride estado de jogadores
      if (this.estadoVotacao === 'pontos') {
        this.estadoVotacao = 'horas';
      }
    }
    // Se pontosRevelados = false: não altera — jogador pode já ter votado pontos localmente
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

  votarPontosDireto(valor: number): void {
    if (this.votando) return;
    this.pontoSelecionado = valor;
    this.cdr.detectChanges();
    this.votarPontos();
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
        this.estadoVotacao = 'horas';
        this.erro = '';
        this.atualizarEstimativas();
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        if (err.status === 409) {
          this.erro = err.error?.message || 'Você já votou nesta tarefa.';
          this.estadoVotacao = 'horas';
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
          const self = res.find((e: any) => e.participante === this.participante);
          if (self) {
            // horas === null → não votou horas; "🔒" ou valor → votou
            const votouHoras = self.horas !== null && self.horas !== undefined;
            if (!votouHoras) {
              this.estadoVotacao = 'horas';
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

  resetar(): void {
    this.estimationService.resetar(this.taskId!).subscribe({
      next: () => {
        this.pontoSelecionado = null;
        this.horaSelecionada = 0;
        this.estadoVotacao = 'pontos';
        this.todosVotaram = false;
        this.atualizarEstimativas();
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }
}
