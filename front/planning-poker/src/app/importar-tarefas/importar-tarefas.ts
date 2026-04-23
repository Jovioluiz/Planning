import * as Papa from 'papaparse';
import { ChangeDetectorRef, Component, EnvironmentInjector, OnDestroy, OnInit, runInInjectionContext } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
export class ImportarTarefas implements OnInit, OnDestroy {
  usuario: string | null = '';
  tarefas: ITask[] = [];
  tarefaEmVotacao: ITask | null = null;
  tarefasFila: ITask[] = [];
  tarefasEstimadas: ITask[] = [];
  estimativas: any[] = [];
  participantesTarefa: string[] = [];
  podeRevelarPontos = false;
  podeRevelarHoras = false;
  importando = false;
  tarefaSelecionadaId: number | null = null;
  sprintImport = '';

  mensagem = '';
  mensagemTipo: 'sucesso' | 'erro' | '' = '';
  tempoDecorrido = '00:00';

  private pollInterval: any = null;
  private timerInterval: any = null;

  constructor(
    private taskService: TaskService,
    private router: Router,
    private auth: AuthService,
    private estimationService: EstimationService,
    private cdr: ChangeDetectorRef,
    private injector: EnvironmentInjector
  ) {}

  get sprintAtual(): string | null {
    return this.tarefaEmVotacao?.sprint
      ?? this.tarefasFila[0]?.sprint
      ?? null;
  }

  get tarefasEstimadasDaSprintAtual(): any[] {
    if (!this.sprintAtual) return this.tarefasEstimadas;
    return this.tarefasEstimadas.filter(t => t.sprint === this.sprintAtual);
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
    const valores: number[] = this.estimativas
      .map((e: any) => e.pontos)
      .filter((p: any) => p !== null && p !== undefined && p > 0)
      .sort((a: number, b: number) => a - b);
    if (valores.length === 0) return null;
    const mid = Math.floor(valores.length / 2);
    return valores.length % 2 !== 0 ? valores[mid] : (valores[mid - 1] + valores[mid]) / 2;
  }

  get nivelDivergencia(): 'consenso' | 'proximo' | 'divergente' | null {
    if (!this.tarefaEmVotacao?.pontosRevelados) return null;
    const valores: number[] = this.estimativas
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
    if (!this.tarefaEmVotacao?.pontosRevelados || pontos === null || pontos === undefined || pontos === 0) return null;
    const mediana = this.calcMedianaPontos();
    if (mediana === null) return null;
    const diff = this.closestFibIndex(pontos) - this.closestFibIndex(mediana);
    if (diff <= -2) return 'otimista';
    if (diff >= 2) return 'pessimista';
    return null;
  }

  get quemNaoVotou(): string[] {
    const votaram = new Set(this.estimativas.map((e: any) => e.participante));
    return this.participantesTarefa.filter(p => !votaram.has(p));
  }

  get estatisticasPontos(): { media: number; mediana: number; min: number; max: number; spread: number; cafe: number; total: number } | null {
    if (!this.tarefaEmVotacao?.pontosRevelados) return null;
    const cafe = this.estimativas.filter((e: any) => e.pontos === 0 || e.pontos === null || e.pontos === undefined).length;
    const valores: number[] = this.estimativas
      .map((e: any) => e.pontos)
      .filter((p: any) => p !== null && p !== undefined && p > 0)
      .sort((a: number, b: number) => a - b);
    const total = this.estimativas.length;
    if (valores.length === 0) return { media: 0, mediana: 0, min: 0, max: 0, spread: 0, cafe, total };
    const soma = valores.reduce((a, b) => a + b, 0);
    const media = Math.round((soma / valores.length) * 10) / 10;
    const mid = Math.floor(valores.length / 2);
    const mediana = valores.length % 2 !== 0
      ? valores[mid]
      : Math.round(((valores[mid - 1] + valores[mid]) / 2) * 10) / 10;
    return { media, mediana, min: valores[0], max: valores[valores.length - 1], spread: valores[valores.length - 1] - valores[0], cafe, total };
  }

  get estatisticasHoras(): { media: number; mediana: number; min: number; max: number } | null {
    if (!this.tarefaEmVotacao?.horasReveladas) return null;
    const valores: number[] = this.estimativas
      .map((e: any) => e.horas)
      .filter((h: any) => h !== null && h !== undefined && typeof h === 'number' && h > 0)
      .sort((a: number, b: number) => a - b);
    if (valores.length === 0) return null;
    const soma = valores.reduce((a, b) => a + b, 0);
    const media = Math.round((soma / valores.length) * 10) / 10;
    const mid = Math.floor(valores.length / 2);
    const mediana = valores.length % 2 !== 0
      ? valores[mid]
      : Math.round(((valores[mid - 1] + valores[mid]) / 2) * 10) / 10;
    return { media, mediana, min: valores[0], max: valores[valores.length - 1] };
  }

  ngOnInit(): void {
    this.usuario = this.auth.getUsuario();
    this.carregarListas();
    this.carregarTarefaEmVotacao();
    // Polling a cada 5s para atualizar status dos votos em tempo real
    this.pollInterval = setInterval(() => this.atualizarStatusVotacao(), 5000);
  }

  ngOnDestroy(): void {
    if (this.pollInterval) clearInterval(this.pollInterval);
    if (this.timerInterval) clearInterval(this.timerInterval);
  }

  private iniciarTimer(): void {
    if (this.timerInterval) clearInterval(this.timerInterval);
    if (this.tarefaEmVotacao?.horasReveladas) return;
    this.timerInterval = setInterval(() => {
      const inicio = this.tarefaEmVotacao?.liberadaEm
        ? new Date(this.tarefaEmVotacao.liberadaEm).getTime()
        : null;
      if (inicio) {
        const diff = Math.floor((Date.now() - inicio) / 1000);
        const mins = Math.floor(diff / 60).toString().padStart(2, '0');
        const secs = (diff % 60).toString().padStart(2, '0');
        this.tempoDecorrido = `${mins}:${secs}`;
        this.cdr.detectChanges();
      }
    }, 1000);
  }

  private atualizarStatusVotacao(): void {
    if (!this.tarefaEmVotacao) return;
    const id = this.tarefaEmVotacao.id;
    this.carregarResumoVotos(id);
    this.verificarLiberacoes(id);
    this.carregarParticipantesTarefa(id);
    // Atualiza pontosRevelados/horasReveladas da tarefa em votação
    this.taskService.getTaskById(id.toString()).subscribe({
      next: (t) => { this.tarefaEmVotacao = t; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  carregarParticipantesTarefa(taskId: number): void {
    this.taskService.getParticipantesTarefa(taskId.toString()).subscribe({
      next: (res) => { this.participantesTarefa = res; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  // ChangeDetectorRef.detectChanges() garante que a view atualize no modo zoneless
  private exibirMensagem(texto: string, tipo: 'sucesso' | 'erro'): void {
    this.mensagem = texto;
    this.mensagemTipo = tipo;
    this.cdr.detectChanges();
    setTimeout(() => {
      this.mensagem = '';
      this.mensagemTipo = '';
      this.cdr.detectChanges();
    }, 5000);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];

    if (!file) return;

    if (!this.sprintImport.trim()) {
      this.mensagem = 'Informe a Sprint antes de importar.';
      this.mensagemTipo = 'erro';
      this.cdr.detectChanges();
      setTimeout(() => { this.mensagem = ''; this.mensagemTipo = ''; this.cdr.detectChanges(); }, 5000);
      input.value = '';
      return;
    }

    this.importando = true;
    this.cdr.detectChanges(); // força o "Importando..." aparecer imediatamente

    Papa.parse(file, {
      header: true,
      delimiter: ';',
      skipEmptyLines: true,
      complete: (result: { data: any[] }) => {
        const dados = result.data
          .filter((t: any) => t.numero && t.titulo && t.descricao)
          .map((t: any) => ({
            numero: Number(t.numero),  // CSV retorna strings; backend espera Long
            titulo: String(t.titulo).trim(),
            descricao: String(t.descricao).trim(),
            sprint: this.sprintImport.trim() || null
          }))
          .filter((t: any) => !isNaN(t.numero) && t.numero > 0);

        if (dados.length === 0) {
          this.importando = false;
          this.cdr.detectChanges();
          this.exibirMensagem('Nenhuma tarefa válida encontrada no CSV. Verifique as colunas: numero, titulo, descricao.', 'erro');
          input.value = '';
          return;
        }

        this.taskService.importarCSV(dados).subscribe({
          next: (res: any) => {
            const msg = res?.message ?? `${dados.length} tarefa(s) importada(s) com sucesso!`;
            this.exibirMensagem(msg, 'sucesso');
            this.tarefaSelecionadaId = null;
            this.importando = false;
            this.cdr.detectChanges();
            this.carregarFilaTarefas();
            this.carregarListas();
            input.value = '';
          },
          error: (err: any) => {
            const msg = err?.error?.message
              ?? (err?.error?.errors ? 'Validação: ' + Object.values(err.error.errors).join('; ') : null)
              ?? `Erro ${err?.status ?? ''}: falha ao importar tarefas.`;
            this.exibirMensagem(msg, 'erro');
            this.importando = false;
            this.cdr.detectChanges();
            input.value = '';
          }
        });
      },
      error: () => {
        this.importando = false;
        this.cdr.detectChanges();
        this.exibirMensagem('Falha ao processar o arquivo CSV.', 'erro');
      }
    });
  }

  // ─── Seleção de tarefa (comportamento de radio) ───────────
  selecionarTarefa(id: number): void {
    this.tarefaSelecionadaId = this.tarefaSelecionadaId === id ? null : id;
  }

  iniciarVotacaoSelecionada(): void {
    if (this.tarefaSelecionadaId !== null) {
      this.iniciarEstimativa(this.tarefaSelecionadaId);
      this.tarefaSelecionadaId = null;
    }
  }

  excluirSelecionada(): void {
    if (this.tarefaSelecionadaId !== null) {
      this.removerTarefaEstimativa(this.tarefaSelecionadaId);
      this.tarefaSelecionadaId = null;
    }
  }

  // ─── Carregamento de dados ───────────────────────────────
  carregarListas(): void {
    this.taskService.getTarefasFila().subscribe({
      next: (res) => { this.tarefasFila = res; this.cdr.detectChanges(); },
      error: () => {} // erros de fundo não exibem mensagem na abertura da tela
    });
    this.taskService.getTarefasVotadas().subscribe({
      next: (res) => { this.tarefasEstimadas = res; this.cdr.detectChanges(); },
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
          this.carregarParticipantesTarefa(this.tarefaEmVotacao.id);
          this.carregarFilaTarefas();
          this.iniciarTimer();
        } else {
          this.tarefaEmVotacao = null;
          this.participantesTarefa = [];
          this.tempoDecorrido = '00:00';
          if (this.timerInterval) { clearInterval(this.timerInterval); this.timerInterval = null; }
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.tarefaEmVotacao = null;
        this.cdr.detectChanges();
      }
    });
  }

  removerParticipante(participante: string): void {
    if (!this.tarefaEmVotacao) return;
    this.taskService.removerParticipante(this.tarefaEmVotacao.id.toString(), participante).subscribe({
      next: () => {
        this.carregarParticipantesTarefa(this.tarefaEmVotacao!.id);
        this.carregarResumoVotos(this.tarefaEmVotacao!.id);
        this.verificarLiberacoes(this.tarefaEmVotacao!.id);
      },
      error: () => this.exibirMensagem('Erro ao remover participante.', 'erro')
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
      next: (dados) => { this.tarefasFila = dados; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  carregarResumoVotos(taskId: number): void {
    this.estimationService.getResumoVotos(taskId.toString()).subscribe({
      next: (res: any[]) => { this.estimativas = res; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  verificarLiberacoes(taskId: number): void {
    this.estimationService.todosVotaramPontos(taskId.toString()).subscribe(res => {
      this.podeRevelarPontos = res;
      this.cdr.detectChanges();
    });
    this.estimationService.todosVotaramHoras(taskId.toString()).subscribe(res => {
      this.podeRevelarHoras = res && !!this.tarefaEmVotacao?.pontosRevelados;
      this.cdr.detectChanges();
    });
  }

  liberarHorasVotacao(): void {
    if (!this.tarefaEmVotacao) return;
    this.taskService.liberarHorasVotacao(this.tarefaEmVotacao.id.toString()).subscribe({
      next: () => this.carregarTarefaEmVotacao(),
      error: () => this.exibirMensagem('Erro ao liberar votação de horas.', 'erro')
    });
  }

  revelarHoras(): void {
    this.estimationService.revelarHoras(this.tarefaEmVotacao!.id.toString()).subscribe({
      next: () => {
        if (this.timerInterval) { clearInterval(this.timerInterval); this.timerInterval = null; }
        this.carregarResumoVotos(this.tarefaEmVotacao!.id);
        this.verificarLiberacoes(this.tarefaEmVotacao!.id);
        this.carregarTarefaEmVotacao();
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
        this.carregarTarefaEmVotacao(); // atualiza pontosRevelados para habilitar revelar horas
      },
      error: () => this.exibirMensagem('Erro ao revelar pontos.', 'erro')
    });
  }

  finalizarVotacao(): void {
    if (!this.tarefaEmVotacao) return;
    this.taskService.finalizarTarefa(this.tarefaEmVotacao.id.toString()).subscribe({
      next: () => {
        this.tarefaEmVotacao = null;
        this.estimativas = [];
        this.participantesTarefa = [];
        this.podeRevelarPontos = false;
        this.podeRevelarHoras = false;
        this.tempoDecorrido = '00:00';
        if (this.timerInterval) { clearInterval(this.timerInterval); this.timerInterval = null; }
        this.carregarListas();
        this.cdr.detectChanges();
      },
      error: () => this.exibirMensagem('Erro ao finalizar tarefa.', 'erro')
    });
  }

  pularVotacao(): void {
    if (!this.tarefaEmVotacao) return;
    this.taskService.pularTarefa(this.tarefaEmVotacao.id.toString()).subscribe({
      next: () => {
        this.tarefaEmVotacao = null;
        this.estimativas = [];
        this.participantesTarefa = [];
        this.podeRevelarPontos = false;
        this.podeRevelarHoras = false;
        this.tempoDecorrido = '00:00';
        if (this.timerInterval) { clearInterval(this.timerInterval); this.timerInterval = null; }
        this.carregarListas();
        this.cdr.detectChanges();
      },
      error: () => this.exibirMensagem('Erro ao pular tarefa.', 'erro')
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
