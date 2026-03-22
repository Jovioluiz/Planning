import * as Papa from 'papaparse';
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { TaskService } from '../services/task.service';
import { EstimationService } from '../services/estimation.service';

@Component({
  selector: 'app-importar-tarefas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './importar-tarefas.html',
  styleUrls: ['./importar-tarefas.scss']
})

export class ImportarTarefas implements OnInit {
  usuario: string | null = '';
  tarefas: any[] = [];
  tarefaEmVotacao: any = null;
  tarefasFila: any[] = [];
  tarefasEstimadas: any[] = [];
  estimativas: any[] = [];
  podeRevelarPontos = false;
  podeRevelarHoras = false;
  importando = false;

  constructor(
    private taskService: TaskService,
    private router: Router,
    private auth: AuthService,
    private estimationService: EstimationService
  ) {}

  ngOnInit(): void {
    this.usuario = this.auth.getUsuario();
    this.carregarFilaTarefas();
    this.carregarListas();
    this.carregarTarefaEmVotacao();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];

    if (!file) {
      alert('Nenhum arquivo selecionado.');
      return;
    }

    this.importando = true;

    Papa.parse(file, {
      header: true,
      skipEmptyLines: true,
      complete: async (result: { data: any[]; }) => {
        const dados = (result.data as any[]).filter(
          (t: any) => t.numero && t.titulo && t.descricao
        );

        if (dados.length === 0) {
          alert('Nenhuma tarefa válida encontrada no CSV.');
          this.importando = false;
          return;
        }

        this.tarefas = dados;

        try {
          await firstValueFrom(this.taskService.importarCSV(dados));
          alert('Tarefas importadas com sucesso!');
          this.carregarFilaTarefas();
          this.carregarListas();
        } catch (err) {
          console.error('Erro ao importar tarefas:', err);
          alert('Erro ao importar tarefas.');
        } finally {
          this.importando = false;
        }
      },
      error: (err: any) => {
        console.error('Erro ao ler o CSV:', err);
        alert('Falha ao processar o arquivo CSV.');
        this.importando = false;
      }
    });
  }

  carregarListas(): void {
    this.taskService.getTarefasFila().subscribe((res: any[]) => this.tarefasFila = res);
    this.taskService.getTarefasVotadas().subscribe((res: any[]) => this.tarefasEstimadas = res);
  }

  carregarTarefaEmVotacao(): void {
    this.taskService.getTarefasLiberadas().subscribe({
      next: (tarefas: any[]) => {
        if (tarefas.length > 0) {
          this.tarefaEmVotacao = tarefas[0];
          this.carregarResumoVotos(this.tarefaEmVotacao.id);
          this.verificarLiberacoes(this.tarefaEmVotacao.id);
          this.carregarFilaTarefas();
        } else {
          this.tarefaEmVotacao = null;
        }
      },
      error: (err: any) => {
        console.error('Erro ao carregar tarefas liberadas:', err);
        this.tarefaEmVotacao = null;
      }
    });
  }

  iniciarEstimativa(id: string): void {
    this.taskService.liberarTarefa(id).subscribe({
      next: () => {
        this.carregarListas();
        this.carregarTarefaEmVotacao();
        // Admin permanece na tela de controle
      },
      error: (err: any) => console.error('Erro ao liberar tarefa', err)
    });
  }

  removerTarefaEstimativa(id: string): void {
    // CORREÇÃO: adicionado .subscribe() — sem ele a requisição HTTP nunca era enviada
    this.taskService.removerTarefa(id).subscribe({
      next: () => {
        this.carregarFilaTarefas();
        this.carregarListas();
      },
      error: (err: any) => console.error('Erro ao remover tarefa:', err)
    });
  }

  carregarFilaTarefas(): void {
    this.taskService.getTarefasFila().subscribe({
      next: (dados: any[]) => this.tarefasFila = dados,
      error: (err: any) => console.error('Erro ao buscar tarefas na fila', err)
    });
  }

  carregarResumoVotos(taskId: string): void {
    this.estimationService.getResumoVotos(taskId).subscribe({
      next: (res: any[]) => { this.estimativas = res; },
      error: (err: any) => console.error('Erro ao buscar resumo de votos:', err)
    });
  }

  verificarLiberacoes(taskId: string): void {
    this.estimationService.todosVotaramPontos(taskId).subscribe(res => {
      this.podeRevelarPontos = res;
    });

    this.estimationService.todosVotaramHoras(taskId).subscribe(res => {
      this.podeRevelarHoras = res && this.tarefaEmVotacao?.pontosRevelados;
    });
  }

  revelarHoras(): void {
    this.estimationService.revelarHoras(this.tarefaEmVotacao.id).subscribe({
      next: () => {
        this.carregarResumoVotos(this.tarefaEmVotacao.id);
        this.verificarLiberacoes(this.tarefaEmVotacao.id);
      },
      error: (err: any) => console.error('Erro ao revelar horas:', err)
    });
  }

  revelarPontos(): void {
    this.estimationService.revelarPontos(this.tarefaEmVotacao.id).subscribe({
      next: () => {
        this.carregarListas();
        this.carregarResumoVotos(this.tarefaEmVotacao.id);
        this.verificarLiberacoes(this.tarefaEmVotacao.id);
      },
      error: (err: any) => console.error('Erro ao revelar pontos:', err)
    });
  }
}
