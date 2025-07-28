import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TaskService } from '../services/task.service';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { EstimationService } from '../services/estimation.service';
import Papa from 'papaparse';

@Component({
  selector: 'app-importar-tarefas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './importar-tarefas.html',
  styleUrls: ['./importar-tarefas.scss']
})

export class ImportarTarefas implements OnInit {
  usuario: string | null = '';
  csvData: string = '';
  tarefaEmVotacao: any = null;
  tarefas: any[] = [];
  tarefasFila: any[] = [];
  tarefasEstimadas: any[] = [];

  constructor(private taskService: TaskService, private router: Router, private auth: AuthService, private estimationService: EstimationService) {}

  ngOnInit() {
    this.usuario = this.auth.getUsuario();
    this.carregarFilaTarefas();
    this.carregarListas();
    // this.carregarTarefaEmVotacao();
  }

onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];

  if (!file) {
    alert("Nenhum arquivo selecionado.");
    return;
  }

  Papa.parse(file, {
    header: true,
    skipEmptyLines: true,
    complete: async (result) => {
      const dados = result.data.filter((t: any) => t.numero && t.titulo && t.descricao);
      
      if (dados.length === 0) {
        alert("Nenhuma tarefa válida encontrada no CSV.");
        return;
      }

      this.tarefas = dados;

      try {
        await this.taskService.importarCSV(dados).toPromise();
        alert('Tarefas importadas com sucesso!');
      } catch (err) {
        console.error('Erro ao importar tarefas:', err);
        alert('Erro ao importar tarefas.');
      }
    },
    error: (err) => {
      console.error('Erro ao ler o CSV:', err);
      alert('Falha ao processar o arquivo CSV.');
    }
  });
}

carregarListas() {
  this.taskService.getTarefasFila().subscribe(res => this.tarefasFila = res);
  this.taskService.getTarefasLiberadas().subscribe(res => this.tarefaEmVotacao = res);
  this.taskService.getTarefasVotadas().subscribe(res => this.tarefasEstimadas = res);
}

  carregarTarefaEmVotacao() {
    this.taskService.getTarefasLiberadas().subscribe({
      next: (tarefas) => {
        if (tarefas.length > 0) {
          this.tarefaEmVotacao = tarefas.length ? tarefas[0] : null;
        } else {
          this.tarefaEmVotacao = null;
        }
      },
      error: (err) => {
        console.error('Erro ao carregar tarefas liberadas:', err);
        this.tarefaEmVotacao = null;
      }
    });
  }

  iniciarEstimativa(id: string): void {
      this.taskService.liberarTarefa(id).subscribe({
        next: () => {
          alert('Tarefa liberada com sucesso!');
          this.carregarListas();
          // this.carregarTarefaEmVotacao();
          this.router.navigate([`/estimativas/${id}`]);
        },
        error: err => {
          console.error('Erro ao liberar tarefa', err);
        }
    });
  }

  removerTarefaEstimativa(id: string): void {
    console.log('ID', id);
    this.taskService.removerTarefa(id);
    this.carregarFilaTarefas();
  }

  carregarFilaTarefas() {
  this.taskService.getTarefasFila().subscribe({
    next: (dados) => this.tarefasFila = dados,
    error: (err) => console.error('Erro ao buscar tarefas na fila', err)
  });
}

resumoVotos: any[] = [];

carregarResumoVotos(taskId: string): void {
  this.estimationService.getResumoVotos(taskId).subscribe({
    next: (res) => {
      this.resumoVotos = res;
    },
    error: (err) => console.error('Erro ao buscar resumo de votos:', err)
  });
}



}
