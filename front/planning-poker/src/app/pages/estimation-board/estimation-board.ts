import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { EstimationService } from '../../services/estimation.service';
import { TaskService } from '../../services/task.service';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-estimation-board',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './estimation-board.html',
  styleUrls: ['./estimation-board.scss']
})

export class EstimationBoard implements OnInit {
  participante = '';
  task: any = null;
  taskId: string | null = null;
  erro = '';
  estimativas: any[] = [];
  cartas = [1, 2, 3, 5, 8, 13, 21, -1];
  horas = [1, 2, 4, 6, 8, 10, 12, 16, 20, 24];
  tarefa: any = null;
  descricao = '';
  titulo = '';
  estadoVotacao: 'pontos' | 'horas' | 'finalizado' = 'pontos';
  pontoSelecionado: number = 0;
  horaSelecionada: number = 0;
  todosVotaram = false;
  votacaoHorasLiberada = false;

  constructor(private taskService: TaskService, 
              private estimationService: EstimationService, 
              private router: Router, 
              private route: ActivatedRoute) {}


ngOnInit(): void {
    this.participante = sessionStorage.getItem('usuario') || '';
    this.taskId = this.route.snapshot.paramMap.get('id')!;
    this.carregarTarefa();
    this.atualizarEstimativas();

    if (this.participante === 'admin'){
      this.checkTodosVotaram();
    }
}

logout(){
  sessionStorage.removeItem('usuario');
  this.router.navigate(['/login']);
}

carregarTarefa() {
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

votarHoras() {
  if (!this.horaSelecionada) {
    this.erro = "Selecione uma estimativa de horas!";
    return;
  }

  this.estimationService.votarHoras(this.taskId!, this.participante, this.horaSelecionada).subscribe({
    next: () => {
      this.estadoVotacao = 'finalizado';
      this.atualizarEstimativas();
    },
    error: (err) => { this.erro = 'Erro ao votar horas'; }
  });
}


async votarPontos() {
    if (!this.participante.trim()) {
      this.erro = "Informe o nome do participante!";
      return;
    }

    if (!this.pontoSelecionado && this.pontoSelecionado !== 0) {
      this.erro = "Selecione uma carta de pontos!";
      return;
    }

    const pontos = this.pontoSelecionado === -1 ? 0 : this.pontoSelecionado;

    this.estimationService.votar(this.taskId!, this.participante, pontos).subscribe({
      next: () => {
        this.estadoVotacao = 'horas';
        this.erro = '';
        this.atualizarEstimativas();
      },
      error: (err: any) => {
        if (err.status === 409) {
          // Erro de voto duplicado (conflito)
          this.erro = err.error?.message || 'Você já votou nesta tarefa.';
          this.atualizarEstimativas();
        } else {
          this.erro = 'Erro ao votar. Tente novamente.';
        }
        console.error("Erro ao votar:", err);
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

        if (this.participante !== 'admin') {
          const self = res.find((e: any) => e.participante === this.participante);
          if (self && self.pontos > 0 && self.horas === 0) {
            this.estadoVotacao = 'horas';
          } else if (self?.horas > 0) {
            this.estadoVotacao = 'finalizado';
          }
        }
      },
      error: (err) => console.error('Erro ao listar estimativas:', err)
    });
  }


checkTodosVotaram(){
  this.estimationService.todosVotaram(this.taskId!).subscribe({
    next: (res) => this.todosVotaram = res.todosVotaram,
    error: (err) => console.error('Erro ao verificar votos:', err)
  });
}

revelar() {
  this.estimationService.revelar(this.taskId!).subscribe(() => {
    this.atualizarEstimativas();
  });
}

resetar() {
  this.estimationService.resetar(this.taskId!).subscribe(() => {
    this.pontoSelecionado = 0;
    this.horaSelecionada = 0;
    this.estadoVotacao = 'pontos';
    this.atualizarEstimativas();
  });
}

liberarVotacaoHoras() {
  this.votacaoHorasLiberada = true;
}

}
