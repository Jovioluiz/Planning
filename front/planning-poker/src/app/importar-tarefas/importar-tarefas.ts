import * as Papa from 'papaparse';
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { TaskService, ITask } from '../services/task.service';
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
  tarefas: ITask[] = [];
  tarefaEmVotacao: ITask | null = null;
  tarefasFila: ITask[] = [];
  tarefasEstimadas: ITask[] = [];
  estimativas: any[] = [];
  podeRevelarPontos = false;
  podeRevelarHoras = false;
  importando = false;

  // Mensagens de feedback inline (substituem alert())
  mensagem = '';
  mensagemTipo: 'sucesso' | 'erro' | '' = '';

  constructor(
    private taskService: TaskService,
    private router: Router,
    private auth: AuthService,
    private estimationService: EstimationService
  ) {}

  ngOnInit(): void {
    this.usuario = this.auth.getUsuario();
    this.carregarListas();
    this.carregarTarefaEmVotacao();
  }

  private exibirMensagem(texto: string, tipo: 'sucesso' | 'erro'): void {
    this.mensagem = texto;
    this.mensagemTipo = tipo;
    setTimeout(() => { this.mensagem = ''; this.mensagemTipo = ''; }, 5000);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];

    if (!file) {
      this.exibirMensagem('Nenhum arquivo selecionado.', 'erro');
      return;
    }

    this.importando = true;

    Papa.parse(file, {
      header: true,
      skipEmptyLines: true,
      complete: async (result: { data: any[] }) => {
        const dados = result.data.filter(
          (t: any) => t.numero && t.titulo && t.descricao
        );

        if (dados.length === 0) {
          this.exibirMensagem('Nenhuma tarefa válida encontrada no CSV. Verifique as colunas: numero, titulo, descricao.', 'erro');
          this.importando = false;
          return;
        }

        this.tarefas = dados;

        try {
          await firstValueFrom(this.taskService.importarCSV(dados));
          this.exibirMensagem(`${dados.length} tarefa(s) importada(s) com sucesso!`, 'sucesso');
          this.carregarFilaTarefas();
          this.carregarListas();
        } catch {
          this.exibirMensagem('Erro ao importar tarefas. Verifique o servidor e tente novamente.', 'erro');
        } finally {
          this.importando = false;
        }
      },
      error: () => {
        this.exibirMensagem('Falha ao processar o arquivo CSV.', 'erro');
        this.importando = false;
      }
    });
  }

  carregarListas(): void {
    this.taskService.getTarefasFila().subscribe({
      next: (res) => this.tarefasFila = res,
      error: () => this.exibirMensagem('Erro ao carregar fila de tarefas.', 'erro')
    });
    this.taskService.getTarefasVotadas().subscribe({
      next: (res) => this.tarefasEstimadas = res,
      error: () => {}
    });
  }

  carregarTarefaEmVotacao(): void {
    this.taskService.getTarefasLiberadas().subscribe({
      next: (tarefas) => {
        if (tarefas.length > 0) {
          this.tarefaEmVotacao = tarefas[0];
          this.carregarResumoVotos(this.tarefaEmVotacao.id);
          this.verificarLiberacoes(this.tarefaEmVotacao.id);
          this.carregarFilaTarefas();
        } else {
          this.tarefaEmVotacao = null;
        }
      },
      error: () => {
        this.tarefaEmVotacao = null;
        this.exibirMensagem('Erro ao carregar tarefa em votação.', 'erro');
      }
    });
  }

  iniciarEstimativa(id: number): void {
    this.taskService.liberarTarefa(id.toString()).subscribe({
      next: () => {
        this.carregarListas();
        this.carregarTarefaEmVotacao();
      },
      error: () => this.exibirMensagem('Erro ao liberar tarefa.', 'erro')
    });
  }

  removerTarefaEstimativa(id: number): void {
    this.taskService.removerTarefa(id.toString()).subscribe({
      next: () => {
        this.carregarFilaTarefas();
        this.carregarListas();
      },
      error: () => this.exibirMensagem('Erro ao remover tarefa.', 'erro')
    });
  }

  carregarFilaTarefas(): void {
    this.taskService.getTarefasFila().subscribe({
      next: (dados) => this.tarefasFila = dados,
      error: () => {}
    });
  }

  carregarResumoVotos(taskId: number): void {
    this.estimationService.getResumoVotos(taskId.toString()).subscribe({
      next: (res: any[]) => { this.estimativas = res; },
      error: () => {}
    });
  }

  verificarLiberacoes(taskId: number): void {
    this.estimationService.todosVotaramPontos(taskId.toString()).subscribe(res => {
      this.podeRevelarPontos = res;
    });

    this.estimationService.todosVotaramHoras(taskId.toString()).subscribe(res => {
      this.podeRevelarHoras = res && !!this.tarefaEmVotacao?.pontosRevelados;
    });
  }

  revelarHoras(): void {
    this.estimationService.revelarHoras(this.tarefaEmVotacao!.id.toString()).subscribe({
      next: () => {
        this.carregarResumoVotos(this.tarefaEmVotacao!.id);
        this.verificarLiberacoes(this.tarefaEmVotacao!.id);
      },
      error: () => this.exibirMensagem('Erro ao revelar horas.', 'erro')
    });
  }

  revelarPontos(): void {
    this.estimationService.revelarPontos(this.tarefaEmVotacao!.id.toString()).subscribe({
      next: () => {
        this.carregarListas();
        this.carregarResumoVotos(this.tarefaEmVotacao!.id);
        this.verificarLiberacoes(this.tarefaEmVotacao!.id);
      },
      error: () => this.exibirMensagem('Erro ao revelar pontos.', 'erro')
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
