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
  cartas = [1, 2, 3, 5, 8, 13, 21, '?'];
  tarefa: any = null;
  descricao = '';
  titulo = '';

  constructor(private taskService: TaskService, 
              private estimationService: EstimationService, 
              private router: Router, 
              private route: ActivatedRoute) {}


  ngOnInit(): void {
    this.participante = localStorage.getItem('usuario') || '';
    this.taskId = this.route.snapshot.paramMap.get('id')!;
    console.log("ID TASK: ", this.taskId);
    this.carregarTarefa();
    this.atualizarEstimativas();
}

  carregarTarefa() {
    this.taskService.getTaskById(this.taskId!).subscribe({
      next: (dados) => this.tarefa = dados,
      error: (err) => console.error('Erro ao carregar tarefa', err)
    });
  }

votarCarta(carta: number | string) {
  const pontos = carta === '?' ? -1 : carta;
  this.votar(pontos);
}


async votar(pontos: number | string) {
    if (!this.participante.trim()) {
      this.erro = "Informe o nome do participante!";
      return;
    }

    this.estimationService.votar(this.taskId!, this.participante, pontos).subscribe({
      next: () => {
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

  atualizarEstimativas() {
    this.estimationService.listar(this.taskId!).subscribe({
      next: (dados: any[] )=> this.estimativas = dados,
      error: err => console.error('Erro ao listar estimativas:', err)
    });
}

  revelar() {
    this.estimationService.revelar(this.taskId!).subscribe(() => this.atualizarEstimativas());
  }

  resetar() {
    this.estimationService.resetar(this.taskId!).subscribe(() => this.atualizarEstimativas());
  }

}
