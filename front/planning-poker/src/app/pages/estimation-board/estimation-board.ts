import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { EstimationService } from '../../services/estimation.service';
import { TaskService } from '../../services/task.service';
import { WebSocketService } from '../../websocket/websocket.service';

// Sentinela para a carta "café" — valor distinto de 0 para não confundir com "não votou"
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
  task: any = null;
  taskId: string | null = null;
  erro = '';
  estimativas: any[] = [];
  cartas = [1, 2, 3, 5, 8, 13, 21, CARTA_CAFE];
  horas = [1, 2, 4, 6, 8, 10, 12, 16, 20, 24];
  tarefa: any = null;
  estadoVotacao: 'pontos' | 'horas' | 'finalizado' = 'pontos';
  pontoSelecionado: number | null = null;
  horaSelecionada: number = 0;
  todosVotaram = false;
  readonly CARTA_CAFE = CARTA_CAFE;

  constructor(
    private taskService: TaskService,
    private estimationService: EstimationService,
    private router: Router,
    private route: ActivatedRoute,
    private wsService: WebSocketService,
    private auth: AuthService
  ) {}

  get isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  ngOnInit(): void {
    this.participante = this.auth.getUsuario() || '';
    this.taskId = this.route.snapshot.paramMap.get('id')!;
    this.carregarTarefa();
    this.atualizarEstimativas();

    if (this.isAdmin) {
      this.checkTodosVotaram();
    }

    this.wsService.subscribe('/topic/estimativas', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.taskId === this.taskId) {
        if (data.acao === 'REVELAR_PONTOS' || data.acao === 'REVELAR_HORAS') {
          this.atualizarEstimativas();
          this.carregarTarefa();
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.wsService.unsubscribe('/topic/estimativas');
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  carregarTarefa(): void {
    this.taskService.getTaskById(this.taskId!).subscribe(tarefa => {
      this.tarefa = tarefa;

      if (tarefa.pontosRevelados && tarefa.horasReveladas) {
        this.estadoVotacao = 'finalizado';
      } else if (tarefa.pontosRevelados) {
        this.estadoVotacao = 'horas';
      } else {
        this.estadoVotacao = 'pontos';
      }
    });
  }

  votarHoras(): void {
    if (!this.horaSelecionada) {
      this.erro = 'Selecione uma estimativa de horas!';
      return;
    }

    this.estimationService.votarHoras(this.taskId!, this.participante, this.horaSelecionada).subscribe({
      next: () => {
        this.estadoVotacao = 'finalizado';
        this.atualizarEstimativas();
      },
      error: () => { this.erro = 'Erro ao votar horas'; }
    });
  }

  votarPontosDireto(valor: number): void {
    this.pontoSelecionado = valor;
    this.votarPontos();
  }

  votarPontos(): void {
    if (!this.participante.trim()) {
      this.erro = 'Informe o nome do participante!';
      return;
    }

    if (this.pontoSelecionado === null) {
      this.erro = 'Selecione uma carta de pontos!';
      return;
    }

    // Carta café envia valor 0 para o backend, mas controlamos o estado pelo sentinela CARTA_CAFE
    const pontos = this.pontoSelecionado === CARTA_CAFE ? 0 : this.pontoSelecionado;

    this.estimationService.votar(this.taskId!, this.participante, pontos).subscribe({
      next: () => {
        this.estadoVotacao = 'horas';
        this.erro = '';
        this.atualizarEstimativas();
      },
      error: (err: any) => {
        if (err.status === 409) {
          this.erro = err.error?.message || 'Você já votou nesta tarefa.';
          this.atualizarEstimativas();
        } else {
          this.erro = 'Erro ao votar. Tente novamente.';
        }
      }
    });
  }

  atualizarEstimativas(): void {
    this.estimationService.listar(this.taskId!).subscribe({
      next: (res) => {
        this.estimativas = res.map((est: any) => ({
          user: est.participante,
          Pontos: est.revealed ? est.pontos : '🔒',
          Horas: est.revealed ? est.horas : '🔒'
        }));

        if (!this.isAdmin) {
          const self = res.find((e: any) => e.participante === this.participante);
          if (self) {
            // Verifica por pontos votados: null/undefined = não votou, qualquer número (incluindo 0 do café) = votou
            const votouPontos = self.pontos !== null && self.pontos !== undefined;
            const votouHoras = self.horas !== null && self.horas !== undefined && self.horas > 0;

            if (votouPontos && !votouHoras) {
              this.estadoVotacao = 'horas';
            } else if (votouHoras) {
              this.estadoVotacao = 'finalizado';
            }
          }
        }

        if (this.isAdmin) {
          this.checkTodosVotaram();
        }
      },
      error: (err) => console.error('Erro ao listar estimativas:', err)
    });
  }

  checkTodosVotaram(): void {
    this.estimationService.todosVotaram(this.taskId!).subscribe({
      next: (res) => this.todosVotaram = res.todosVotaram,
      error: (err) => console.error('Erro ao verificar votos:', err)
    });
  }

  revelar(): void {
    this.estimationService.revelar(this.taskId!).subscribe(() => {
      this.atualizarEstimativas();
    });
  }

  resetar(): void {
    this.estimationService.resetar(this.taskId!).subscribe(() => {
      this.pontoSelecionado = null;
      this.horaSelecionada = 0;
      this.estadoVotacao = 'pontos';
      this.atualizarEstimativas();
    });
  }
}
