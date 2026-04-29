import * as Papa from 'papaparse';
import { forkJoin } from 'rxjs';
import { ChangeDetectorRef, Component, EnvironmentInjector, OnDestroy, OnInit, runInInjectionContext } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { TaskService, ITask } from '../services/task.service';
import { EstimationService } from '../services/estimation.service';
import { WebSocketService } from '../websocket/websocket.service';

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
  formatoExport: 'csv' | 'json' = 'csv';
  exportando = false;
  usuariosOnline: string[] = [];
  temaEscuro = localStorage.getItem('tema') === 'escuro';
  horasFinaisPorTarefa = new Map<number, number>();

  private pollInterval: any = null;
  private timerInterval: any = null;

  constructor(
    private taskService: TaskService,
    private router: Router,
    private auth: AuthService,
    private estimationService: EstimationService,
    private cdr: ChangeDetectorRef,
    private injector: EnvironmentInjector,
    private wsService: WebSocketService
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

  get totalHorasEstimadas(): number {
    const total = this.tarefasEstimadasDaSprintAtual
      .reduce((sum, t) => sum + (this.horasFinaisPorTarefa.get(t.id) ?? 0), 0);
    return Math.round(total * 10) / 10;
  }

  get tarefasComHorasCount(): number {
    return this.tarefasEstimadasDaSprintAtual
      .filter(t => (this.horasFinaisPorTarefa.get(t.id) ?? 0) > 0).length;
  }

  private readonly FIBONACCI = [1, 2, 3, 5, 8, 13, 21];

  get rodadaAtualNum(): number {
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
    if (!this.tarefaEmVotacao?.pontosRevelados) return null;
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
    if (!this.tarefaEmVotacao?.pontosRevelados || pontos === null || pontos === undefined || pontos === 0) return null;
    const mediana = this.calcMedianaPontos();
    if (mediana === null) return null;
    const diff = this.closestFibIndex(pontos) - this.closestFibIndex(mediana);
    if (diff <= -2) return 'otimista';
    if (diff >= 2) return 'pessimista';
    return null;
  }

  get quemNaoVotou(): string[] {
    const votaram = new Set(this.estimativasRodadaAtual.map((e: any) => e.participante));
    return this.participantesTarefa
      .filter(p => this.usuariosOnline.includes(p))
      .filter(p => !votaram.has(p));
  }

  get estatisticasPontos(): { media: number; mediana: number; min: number; max: number; spread: number; cafe: number; total: number } | null {
    if (!this.tarefaEmVotacao?.pontosRevelados) return null;
    const cafe = this.estimativasRodadaAtual.filter((e: any) => e.pontos === 0 || e.pontos === null || e.pontos === undefined).length;
    const valores: number[] = this.estimativasRodadaAtual
      .map((e: any) => e.pontos)
      .filter((p: any) => p !== null && p !== undefined && p > 0)
      .sort((a: number, b: number) => a - b);
    const total = this.estimativasRodadaAtual.length;
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
    const valores: number[] = this.estimativasRodadaAtual
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
    this.carregarUsuariosOnline();
    this.wsService.subscribe('/topic/sessoes', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.acao === 'USUARIO_CONECTADO' && !this.usuariosOnline.includes(data.usuario)) {
        this.usuariosOnline = [...this.usuariosOnline, data.usuario];
      } else if (data.acao === 'USUARIO_DESCONECTADO') {
        this.usuariosOnline = this.usuariosOnline.filter(u => u !== data.usuario);
      }
      this.cdr.detectChanges();
    });
    // Polling a cada 5s para atualizar status dos votos em tempo real
    this.pollInterval = setInterval(() => this.atualizarStatusVotacao(), 5000);
  }

  ngOnDestroy(): void {
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
    // Carrega tudo em paralelo; verificarLiberacoes roda depois que estimativas atualizar
    this.carregarParticipantesTarefa(id);
    this.carregarResumoVotos(id, () => this.verificarLiberacoes(id));
    this.taskService.getTaskById(id.toString()).subscribe({
      next: (t) => { this.tarefaEmVotacao = t; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  carregarParticipantesTarefa(taskId: number): void {
    this.taskService.getParticipantesTarefa(taskId.toString()).subscribe({
      next: (res) => { this.participantesTarefa = res; this.verificarLiberacoes(taskId); this.cdr.detectChanges(); },
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
      error: () => {}
    });
    this.taskService.getTarefasVotadas().subscribe({
      next: (res) => {
        this.tarefasEstimadas = res;
        this.carregarHorasEstimadas();
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }

  private carregarHorasEstimadas(): void {
    const semCache = this.tarefasEstimadas.filter(t => !this.horasFinaisPorTarefa.has(t.id));
    if (!semCache.length) return;
    forkJoin(semCache.map(t => this.estimationService.getResumoVotos(t.id.toString()))).subscribe({
      next: (resultados) => {
        semCache.forEach((t, i) => {
          const s = this.calcularEstatisticasTarefa(resultados[i]);
          this.horasFinaisPorTarefa.set(t.id, s.horasMedia ?? 0);
        });
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }

  carregarTarefaEmVotacao(): void {
    this.taskService.getTarefasLiberadas().subscribe({
      next: (tarefas) => {
        if (tarefas.length > 0) {
          this.tarefaEmVotacao = tarefas[0];
          this.carregarResumoVotos(this.tarefaEmVotacao.id, () => this.verificarLiberacoes(this.tarefaEmVotacao!.id));
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
        this.carregarResumoVotos(this.tarefaEmVotacao!.id, () => this.verificarLiberacoes(this.tarefaEmVotacao!.id));
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

  carregarResumoVotos(taskId: number, callback?: () => void): void {
    this.estimationService.getResumoVotos(taskId.toString()).subscribe({
      next: (res: any[]) => {
        this.estimativas = res;
        this.cdr.detectChanges();
        callback?.();
      },
      error: () => {}
    });
  }

  verificarLiberacoes(_taskId: number): void {
    // quem deve votar = participantes da sprint que estão online agora
    const deveVotar = this.participantesTarefa.filter(p => this.usuariosOnline.includes(p));

    const votaramPontos = new Set(
      this.estimativasRodadaAtual.map((e: any) => e.participante)
    );
    this.podeRevelarPontos = deveVotar.length > 0 && deveVotar.every(p => votaramPontos.has(p));

    const votaramHoras = new Set(
      this.estimativasRodadaAtual
        .filter((e: any) => e.horas !== null && e.horas !== undefined)
        .map((e: any) => e.participante)
    );
    this.podeRevelarHoras = !!this.tarefaEmVotacao?.pontosRevelados
      && deveVotar.length > 0
      && deveVotar.every(p => votaramHoras.has(p));

    this.cdr.detectChanges();
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
        this.carregarResumoVotos(this.tarefaEmVotacao!.id, () => this.verificarLiberacoes(this.tarefaEmVotacao!.id));
        this.carregarTarefaEmVotacao();
      },
      error: () => this.exibirMensagem('Erro ao revelar horas.', 'erro')
    });
  }

  revelarPontos(): void {
    this.estimationService.revelarPontos(this.tarefaEmVotacao!.id.toString()).subscribe({
      next: () => {
        this.carregarListas();
        this.carregarResumoVotos(this.tarefaEmVotacao!.id, () => this.verificarLiberacoes(this.tarefaEmVotacao!.id));
        this.carregarTarefaEmVotacao(); // atualiza pontosRevelados para habilitar revelar horas
      },
      error: () => this.exibirMensagem('Erro ao revelar pontos.', 'erro')
    });
  }

  finalizarVotacao(): void {
    if (!this.tarefaEmVotacao) return;
    const s = this.calcularEstatisticasTarefa(this.estimativas);
    this.horasFinaisPorTarefa.set(this.tarefaEmVotacao.id, s.horasMedia ?? 0);
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

  private calcularEstatisticasTarefa(estimativas: any[]): {
    pontoMediana: number | null; pontoMedia: number | null;
    horasMedia: number | null; cafeCount: number;
  } {
    const maxRodada = estimativas.length > 0 ? Math.max(...estimativas.map(e => e.rodada ?? 1)) : 1;
    const estFinal = estimativas.filter(e => (e.rodada ?? 1) === maxRodada);
    const cafeCount = estFinal.filter(e => e.pontos === 0 || e.pontos === null || e.pontos === undefined).length;
    const pontos = estFinal
      .map(e => e.pontos)
      .filter((p): p is number => p !== null && p !== undefined && p > 0)
      .sort((a, b) => a - b);
    const horas = estFinal
      .map(e => e.horas)
      .filter((h): h is number => h !== null && h !== undefined && typeof h === 'number' && h > 0);

    const pontoMedia = pontos.length
      ? Math.round(pontos.reduce((a, b) => a + b, 0) / pontos.length * 10) / 10
      : null;
    const mid = Math.floor(pontos.length / 2);
    const pontoMediana = pontos.length
      ? (pontos.length % 2 !== 0 ? pontos[mid] : Math.round((pontos[mid - 1] + pontos[mid]) / 2 * 10) / 10)
      : null;
    const horasMedia = horas.length
      ? Math.round(horas.reduce((a, b) => a + b, 0) / horas.length * 10) / 10
      : null;

    return { pontoMediana, pontoMedia, horasMedia, cafeCount };
  }

  exportarEstimativas(): void {
    const tarefas = this.tarefasEstimadasDaSprintAtual;
    if (!tarefas.length) return;

    this.exportando = true;
    this.cdr.detectChanges();

    forkJoin(tarefas.map(t => this.estimationService.getResumoVotos(t.id.toString()))).subscribe({
      next: (resultados) => {
        const linhas = tarefas.map((t, i) => {
          const s = this.calcularEstatisticasTarefa(resultados[i]);
          const tempoTotalSeg = (t as any).estimadaEm && (t as any).liberadaEm
            ? Math.floor((new Date((t as any).estimadaEm).getTime() - new Date((t as any).liberadaEm).getTime()) / 1000)
            : null;
          return {
            numero: t.numero,
            titulo: t.titulo,
            descricao: t.descricao ?? '',
            sprint: t.sprint ?? '',
            pontos_mediana: s.pontoMediana ?? '',
            pontos_media: s.pontoMedia ?? '',
            horas_media: s.horasMedia ?? '',
            carta_cafe_count: s.cafeCount,
            tempo_total_estimacao: tempoTotalSeg != null ? this.formatarTempo(tempoTotalSeg) : ''
          };
        });

        const sprint = this.sprintAtual ?? 'sprint';
        let conteudo: string;
        let tipo: string;
        let ext: string;

        if (this.formatoExport === 'json') {
          conteudo = JSON.stringify(linhas, null, 2);
          tipo = 'application/json';
          ext = 'json';
        } else {
          conteudo = '﻿' + Papa.unparse(linhas, { delimiter: ';', header: true });
          tipo = 'text/csv;charset=utf-8;';
          ext = 'csv';
        }

        const blob = new Blob([conteudo], { type: tipo });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `estimativas_sprint_${sprint}.${ext}`;
        a.click();
        URL.revokeObjectURL(url);

        this.exportando = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.exportando = false;
        this.exibirMensagem('Erro ao exportar estimativas.', 'erro');
        this.cdr.detectChanges();
      }
    });
  }

  novaRodada(): void {
    if (!this.tarefaEmVotacao) return;
    this.estimationService.novaRodada(this.tarefaEmVotacao.id.toString()).subscribe({
      next: () => {
        this.carregarTarefaEmVotacao();
        this.carregarResumoVotos(this.tarefaEmVotacao!.id, () => this.verificarLiberacoes(this.tarefaEmVotacao!.id));
      },
      error: () => this.exibirMensagem('Erro ao iniciar nova rodada.', 'erro')
    });
  }

  formatarTempo(segundos: number | null | undefined): string {
    if (segundos == null || segundos < 0) return '—';
    const m = Math.floor(segundos / 60).toString().padStart(2, '0');
    const s = (segundos % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }

  toggleTema(): void {
    this.temaEscuro = !this.temaEscuro;
    localStorage.setItem('tema', this.temaEscuro ? 'escuro' : 'claro');
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
