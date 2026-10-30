import * as Papa from 'papaparse';
import { firstValueFrom, forkJoin } from 'rxjs';
import { ChangeDetectorRef, Component, EnvironmentInjector, HostListener, OnDestroy, OnInit, runInInjectionContext } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { TaskService, ITask, ValoresFinais } from '../services/task.service';
import { EstimationService } from '../services/estimation.service';
import { WebSocketService } from '../websocket/websocket.service';
import { SalaService } from '../services/sala.service';
import { mensagemDeErro } from '../services/api-jakarta';
import { ConfigurarRedmine } from '../pages/configurar-redmine/configurar-redmine';
import { Estatisticas } from '../pages/estatisticas/estatisticas';
import { textileParaHtml } from './textile-redmine';

/** Colunas extras que repetem campos da tarefa (o CSV guarda "titulo"; o Redmine, "Título"). */
const COLUNAS_PROPRIAS = new Set(['titulo', 'descricao']);

/**
 * Colunas extras mostradas nas tabelas, nesta ordem, quando existem (nomes normalizados). O resto aparece só no
 * painel de detalhe: o Redmine traz dezenas de campos e a tabela ganharia barra de rolagem.
 */
const COLUNAS_TABELA = ['tipo', 'situacao', 'prioridade', 'atribuido para'];
const MAXIMO_COLUNAS_TABELA = COLUNAS_TABELA.length;

const normalizarColuna = (coluna: string) =>
  coluna.trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');

@Component({
  selector: 'app-importar-tarefas',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfigurarRedmine, Estatisticas],
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
  estimativasTeste: any[] = [];
  todosTestadoresVotaram = false;
  importando = false;
  tarefaSelecionadaId: number | null = null;
  tarefaDetalhe: ITask | null = null;
  atualizandoDetalhe = false;
  erroAtualizacaoDetalhe = '';
  /** Descrição Textile do chamado já em HTML; vazia enquanto converte ou se a tarefa não veio do Redmine. */
  descricaoHtml = '';
  configurandoRedmine = false;
  vendoEstatisticas = false;

  mensagem = '';
  mensagemTipo: 'sucesso' | 'erro' | '' = '';
  tempoDecorrido = '00:00';
  formatoExport: 'csv' | 'json' = 'csv';
  exportando = false;
  usuariosOnline: string[] = [];
  temaEscuro = localStorage.getItem('tema') === 'escuro';
  horasFinaisPorTarefa = new Map<number, number>();
  linkCopiado = false;
  importandoRedmine = false;

  finalizando = false;

  private pollInterval: any = null;
  private mensagemTimer: any = null;
  private timerInterval: any = null;

  constructor(
    private taskService: TaskService,
    private router: Router,
    private auth: AuthService,
    private estimationService: EstimationService,
    private cdr: ChangeDetectorRef,
    private injector: EnvironmentInjector,
    private wsService: WebSocketService,
    private salaService: SalaService
  ) {}

  get sprintAtual(): string | null {
    const s = this.tarefaEmVotacao?.sprint
      ?? this.tarefasFila[0]?.sprint
      ?? null;
    return s || null;
  }

  get nomeSala(): string | null {
    return this.salaService.getSalaNome();
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

  // Tópico global — recebe USUARIO_CONECTADO / USUARIO_DESCONECTADO (só sem sala)
  private readonly topicSessoes = '/topic/sessoes';
  // Tópico de sala — recebe presença da sala e SALA_INATIVADA (só existe quando há salaId)
  private topicSalaEventos: string | null = null;

  ngOnInit(): void {
    this.usuario = this.auth.getUsuario();

    const salaId = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('salaId') : null;
    if (salaId) {
      this.topicSalaEventos = `/topic/sala/${salaId}/sessoes`;
      this.taskService.registrarPresencaNaSala(salaId).subscribe({
        next: () => this.carregarUsuariosOnline(),
        error: () => {}
      });
    }

    this.carregarListas();
    this.carregarTarefaEmVotacao();
    this.carregarUsuariosOnline();

    // Presença global apenas para o backlog legado; com sala, a presença vem do tópico da sala
    if (!this.topicSalaEventos) {
      this.wsService.subscribe(this.topicSessoes, (msg) => {
        this.atualizarPresenca(JSON.parse(msg.body));
      });
    }

    // Eventos específicos da sala (presença e SALA_INATIVADA)
    if (this.topicSalaEventos) {
      this.wsService.subscribe(this.topicSalaEventos, (msg) => {
        const data = JSON.parse(msg.body);
        this.atualizarPresenca(data);
        if (data.acao === 'SALA_INATIVADA') {
          if (typeof sessionStorage !== 'undefined') {
            sessionStorage.removeItem('salaId');
            sessionStorage.removeItem('salaCodigo');
            sessionStorage.removeItem('salaNome');
          }
          this.router.navigate(['/salas']);
        }
        this.cdr.detectChanges();
      });
    }

    // Polling a cada 5s: atualiza votos e refresca lista de online como fallback
    this.pollInterval = setInterval(() => {
      this.atualizarStatusVotacao();
      this.carregarUsuariosOnline();
    }, 5000);
  }

  private atualizarPresenca(data: any): void {
    if (data.acao === 'USUARIO_CONECTADO' && !this.usuariosOnline.includes(data.usuario)) {
      this.usuariosOnline = [...this.usuariosOnline, data.usuario];
    } else if (data.acao === 'USUARIO_DESCONECTADO') {
      this.usuariosOnline = this.usuariosOnline.filter(u => u !== data.usuario);
    }
    this.cdr.detectChanges();
  }

  ngOnDestroy(): void {
    this.liberarAnexos();
    if (!this.topicSalaEventos) this.wsService.unsubscribe(this.topicSessoes);
    if (this.topicSalaEventos) this.wsService.unsubscribe(this.topicSalaEventos);
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
      next: (t) => {
        if (t.estimada) {
          // Tarefa foi finalizada — limpa o painel de votação
          this.tarefaEmVotacao = null;
          this.estimativas = [];
          this.participantesTarefa = [];
          this.podeRevelarPontos = false;
          this.podeRevelarHoras = false;
          this.tempoDecorrido = '00:00';
          if (this.timerInterval) { clearInterval(this.timerInterval); this.timerInterval = null; }
          this.carregarListas();
        } else {
          this.tarefaEmVotacao = t;
          if (t.horasTesteLiberadas) this.atualizarEstimativasTeste();
        }
        this.cdr.detectChanges();
      },
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
  // O timer da mensagem anterior é cancelado: senão ele apagaria a nova antes da hora
  private exibirMensagem(texto: string, tipo: 'sucesso' | 'erro', duracaoMs = 5000): void {
    this.mensagem = texto;
    this.mensagemTipo = tipo;
    this.cdr.detectChanges();
    if (this.mensagemTimer) clearTimeout(this.mensagemTimer);
    this.mensagemTimer = setTimeout(() => {
      this.mensagem = '';
      this.mensagemTipo = '';
      this.mensagemTimer = null;
      this.cdr.detectChanges();
    }, duracaoMs);
  }

  /**
   * Colunas extras de todas as tarefas, na ordem em que aparecem: a sala pode misturar tarefas de CSVs com
   * cabeçalhos diferentes e do Redmine. Título e descrição ficam de fora: o título tem coluna própria (do campo
   * da tarefa) e a descrição só aparece na votação.
   */
  get colunasExtras(): string[] {
    const colunas = new Set<string>();
    for (const t of [...this.tarefasFila, ...this.tarefasEstimadas]) {
      Object.keys(t.dadosExtras ?? {})
        .filter(c => !COLUNAS_PROPRIAS.has(normalizarColuna(c)))
        .forEach(c => colunas.add(c));
    }
    return [...colunas];
  }

  /**
   * As de {@link COLUNAS_TABELA} que existirem; se não houver nenhuma (CSV com outros cabeçalhos), as primeiras
   * colunas do arquivo, até o mesmo máximo.
   */
  get colunasTabela(): string[] {
    const todas = this.colunasExtras;
    const preferidas = COLUNAS_TABELA
      .map(nome => todas.find(c => normalizarColuna(c) === nome))
      .filter((c): c is string => !!c);
    return preferidas.length > 0 ? preferidas : todas.slice(0, MAXIMO_COLUNAS_TABELA);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.importando = true;
    this.cdr.detectChanges();

    const reader = new FileReader();
    reader.onload = (e) => {
      const buffer = e.target?.result as ArrayBuffer;
      let texto: string;
      try {
        texto = new TextDecoder('utf-8', { fatal: true }).decode(buffer);
        if (texto.charCodeAt(0) === 0xFEFF) texto = texto.slice(1); // remove BOM UTF-8
      } catch {
        texto = new TextDecoder('windows-1252').decode(buffer);
      }

      Papa.parse(texto, {
        header: true,
        delimiter: ';',
        skipEmptyLines: true,
        complete: (result: { data: any[]; meta: { fields?: string[] } }) => {
          const campos = (result.meta.fields ?? []).map((f: string) => f.toLowerCase());
          const faltando = ['numero', 'titulo'].filter(c => !campos.includes(c));
          if (faltando.length > 0) {
            this.importando = false;
            this.exibirMensagem(`Colunas obrigatórias ausentes no CSV: ${faltando.join(', ')}`, 'erro');
            this.cdr.detectChanges();
            input.value = '';
            return;
          }

          const dados = result.data
            .map((t: any) => {
              // Todas as colunas exceto 'numero' viram dadosExtras para exibição dinâmica
              const dadosExtras: Record<string, string> = {};
              Object.keys(t).forEach(key => {
                if (key.toLowerCase() !== 'numero' && t[key] !== '' && t[key] != null) {
                  dadosExtras[key] = String(t[key]).trim();
                }
              });
              const tarefa: any = {
                numero: Number(t.numero ?? t.Numero ?? t.NUMERO),
                // Campos dedicados mapeados para funcionamento do voting board
                titulo: (t.titulo ?? t.Titulo ?? t.TITULO ?? '').toString().trim(),
                descricao: (t.descricao ?? t.Descricao ?? t.DESCRICAO ?? '').toString().trim(),
                sprint: (t.sprint ?? t.Sprint ?? t.SPRINT ?? '').toString().trim()
              };
              if (Object.keys(dadosExtras).length > 0) tarefa.dadosExtras = dadosExtras;
              return tarefa;
            })
            .filter((t: any) => !isNaN(t.numero) && t.numero > 0);

          if (dados.length === 0) {
            this.importando = false;
            this.cdr.detectChanges();
            input.value = '';
            return;
          }

          this.taskService.importarCSVSala(dados).subscribe({
            next: (res: any) => {
              const msg = res?.message ?? `${dados.length} tarefa(s) importada(s) com sucesso!`;
              this.exibirMensagem(msg, 'sucesso');
              this.tarefaSelecionadaId = null;
              this.importando = false;
              this.carregarListas();
              this.cdr.detectChanges();
              input.value = '';
            },
            error: (err: any) => {
              const msg = err?.error?.message
                ?? (err?.error?.errors ? 'Validação: ' + Object.values(err.error.errors).join('; ') : null)
                ?? `Erro ${err?.status ?? ''}: falha ao importar tarefas.`;
              this.exibirMensagem(msg, 'erro');
              this.importando = false;
              this.carregarListas();
              this.cdr.detectChanges();
              input.value = '';
            }
          });
        },
        error: () => {
          this.importando = false;
          this.cdr.detectChanges();
          this.exibirMensagem('Falha ao processar o arquivo CSV.', 'erro');
          input.value = '';
        }
      });
    };
    reader.onerror = () => {
      this.importando = false;
      this.cdr.detectChanges();
      this.exibirMensagem('Falha ao ler o arquivo.', 'erro');
      input.value = '';
    };
    reader.readAsArrayBuffer(file);
  }

  // ─── Seleção de tarefa (comportamento de radio) ───────────
  selecionarTarefa(id: number): void {
    this.tarefaSelecionadaId = this.tarefaSelecionadaId === id ? null : id;
  }

  // ─── Detalhe da tarefa (painel de baixo pra cima) ─────────
  /** Estimada vinda do Redmine: mostra o que está gravado e busca o chamado como está agora no Redmine. */
  abrirDetalhe(tarefa: ITask): void {
    this.liberarAnexos();
    this.tarefaDetalhe = tarefa;
    this.descricaoHtml = '';
    this.renderizarDescricao();
    this.erroAtualizacaoDetalhe = '';
    this.atualizandoDetalhe = !!tarefa.estimada && !!tarefa.doRedmine;
    if (!this.atualizandoDetalhe) return;
    this.taskService.atualizarRedmineSala(tarefa.id.toString()).subscribe({
      next: (atualizada) => {
        this.tarefasEstimadas = this.tarefasEstimadas.map(t => t.id === atualizada.id ? atualizada : t);
        if (this.tarefaDetalhe?.id === atualizada.id) {
          this.tarefaDetalhe = atualizada;
          this.atualizandoDetalhe = false;
          this.renderizarDescricao();
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        if (this.tarefaDetalhe?.id !== tarefa.id) return;
        this.atualizandoDetalhe = false;
        this.erroAtualizacaoDetalhe = 'Não foi possível atualizar do Redmine: '
          + (mensagemDeErro(err?.error) ?? `erro ${err?.status ?? ''}`) + '. Mostrando os dados da importação.';
        this.cdr.detectChanges();
      }
    });
  }

  fecharDetalhe(): void {
    this.tarefaDetalhe = null;
    this.atualizandoDetalhe = false;
    this.liberarAnexos();
  }

  /** Imagens anexadas ao chamado aberto no painel, por nome; os endereços blob: são liberados ao fechar. */
  private anexosDetalhe = new Map<string, Promise<string | null>>();

  private carregarAnexo(tarefaId: number, nome: string): Promise<string | null> {
    let url = this.anexosDetalhe.get(nome);
    if (!url) {
      url = firstValueFrom(this.taskService.anexoRedmineSala(String(tarefaId), nome))
        .then(blob => URL.createObjectURL(blob))
        .catch(() => null);
      this.anexosDetalhe.set(nome, url);
    }
    return url;
  }

  private liberarAnexos(): void {
    this.anexosDetalhe.forEach(url => url.then(u => { if (u) URL.revokeObjectURL(u); }));
    this.anexosDetalhe.clear();
  }

  /** Só o chamado do Redmine vem em Textile; o de CSV fica em texto puro. Mantém o HTML anterior até o novo. */
  private renderizarDescricao(): void {
    const tarefa = this.tarefaDetalhe;
    const texto = this.descricaoDetalhe;
    if (!tarefa?.doRedmine || !texto) {
      this.descricaoHtml = '';
      return;
    }
    textileParaHtml(texto, nome => this.carregarAnexo(tarefa.id, nome)).then(html => {
      if (this.tarefaDetalhe !== tarefa) return;
      this.descricaoHtml = html;
      this.cdr.detectChanges();
    }).catch(() => { /* fica o texto puro */ });
  }

  @HostListener('document:keydown.escape')
  fecharPaineis(): void {
    if (this.tarefaDetalhe) this.fecharDetalhe();
    this.configurandoRedmine = false;
    this.vendoEstatisticas = false;
  }

  get detalheNaFila(): boolean {
    return !!this.tarefaDetalhe && this.tarefasFila.some(t => t.id === this.tarefaDetalhe!.id);
  }

  /** Valor da coluna extra (CSV ou Redmine) com esse nome, sem diferenciar maiúsculas nem acentos. */
  private extraDetalhe(nome: string): string {
    const extras = this.tarefaDetalhe?.dadosExtras ?? {};
    const chave = Object.keys(extras).find(k => normalizarColuna(k) === nome && extras[k]);
    return chave ? String(extras[chave]) : '';
  }

  get tituloDetalhe(): string {
    return this.tarefaDetalhe?.titulo?.trim() || this.extraDetalhe('titulo');
  }

  get descricaoDetalhe(): string {
    return this.tarefaDetalhe?.descricao?.trim() || this.extraDetalhe('descricao');
  }

  /** Demais colunas do CSV/Redmine; título e descrição já aparecem em destaque no painel. */
  get camposDetalhe(): { chave: string; valor: string }[] {
    const extras = this.tarefaDetalhe?.dadosExtras ?? {};
    return Object.keys(extras)
      .filter(k => !COLUNAS_PROPRIAS.has(normalizarColuna(k)))
      .map(k => ({ chave: k, valor: String(extras[k]) }));
  }

  iniciarVotacaoDetalhe(): void {
    if (!this.tarefaDetalhe) return;
    this.iniciarEstimativa(this.tarefaDetalhe.id);
    if (this.tarefaSelecionadaId === this.tarefaDetalhe.id) this.tarefaSelecionadaId = null;
    this.fecharDetalhe();
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
    this.taskService.getTarefasFilaSala().subscribe({
      next: (res) => { this.tarefasFila = res; this.cdr.detectChanges(); },
      error: () => {}
    });
    this.taskService.getTarefasVotadasSala().subscribe({
      next: (res) => {
        this.tarefasEstimadas = res;
        this.carregarHorasEstimadas();
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }

  private carregarHorasEstimadas(): void {
    // Tarefas finalizadas com valor definido pelo moderador não precisam recalcular pelos votos
    this.tarefasEstimadas
      .filter(t => t.horasFinais !== null && t.horasFinais !== undefined)
      .forEach(t => this.horasFinaisPorTarefa.set(t.id, t.horasFinais!));
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
    this.taskService.getTarefasLiberadasSala().subscribe({
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
    this.taskService.removerParticipanteSala(this.tarefaEmVotacao.id.toString(), participante).subscribe({
      next: () => {
        this.carregarParticipantesTarefa(this.tarefaEmVotacao!.id);
        this.carregarResumoVotos(this.tarefaEmVotacao!.id, () => this.verificarLiberacoes(this.tarefaEmVotacao!.id));
      },
      error: () => this.exibirMensagem('Erro ao remover participante.', 'erro')
    });
  }

  iniciarEstimativa(id: number): void {
    this.taskService.liberarTarefaSala(id.toString()).subscribe({
      next: () => {
        this.carregarListas();
        this.carregarTarefaEmVotacao();
      },
      error: () => this.exibirMensagem('Erro ao liberar tarefa.', 'erro')
    });
  }

  removerTarefaEstimativa(id: number): void {
    this.taskService.removerTarefaSala(id.toString()).subscribe({
      next: () => {
        this.carregarFilaTarefas();
        this.carregarListas();
      },
      error: () => this.exibirMensagem('Erro ao remover tarefa.', 'erro')
    });
  }

  carregarFilaTarefas(): void {
    this.taskService.getTarefasFilaSala().subscribe({
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
    this.taskService.liberarHorasVotacaoSala(this.tarefaEmVotacao.id.toString()).subscribe({
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

  /** Carta mais votada na rodada final (ignorando café); em empate, a maior. */
  private sugerirCartaFinal(estimativas: any[]): number | null {
    const maxRodada = estimativas.length > 0 ? Math.max(...estimativas.map(e => e.rodada ?? 1)) : 1;
    const contagem = new Map<number, number>();
    estimativas
      .filter(e => (e.rodada ?? 1) === maxRodada && typeof e.pontos === 'number' && e.pontos > 0)
      .forEach(e => contagem.set(e.pontos, (contagem.get(e.pontos) ?? 0) + 1));
    let melhor: number | null = null;
    contagem.forEach((qtd, carta) => {
      const qtdMelhor = melhor === null ? -1 : contagem.get(melhor)!;
      if (qtd > qtdMelhor || (qtd === qtdMelhor && carta > melhor!)) melhor = carta;
    });
    return melhor;
  }

  finalizarVotacao(): void {
    if (!this.tarefaEmVotacao || this.finalizando) return;
    // Valores finais derivados dos votos da rodada final
    const s = this.calcularEstatisticasTarefa(this.estimativas);
    const valores: ValoresFinais = {
      pontosFinais: this.sugerirCartaFinal(this.estimativas),
      horasFinais: s.horasMedia,
      horasTesteFinais: this.tarefaEmVotacao.horasTesteReveladas ? this.mediaHorasTeste : null
    };
    this.finalizando = true;
    this.cdr.detectChanges();
    this.horasFinaisPorTarefa.set(this.tarefaEmVotacao.id, valores.horasFinais ?? 0);
    const tarefa = this.tarefaEmVotacao; // o next zera tarefaEmVotacao antes do envio ao Redmine
    this.taskService.finalizarTarefaSala(tarefa.id.toString(), valores).subscribe({
      next: () => {
        this.finalizando = false;
        this.tarefaEmVotacao = null;
        this.estimativas = [];
        this.estimativasTeste = [];
        this.todosTestadoresVotaram = false;
        this.participantesTarefa = [];
        this.podeRevelarPontos = false;
        this.podeRevelarHoras = false;
        this.tempoDecorrido = '00:00';
        if (this.timerInterval) { clearInterval(this.timerInterval); this.timerInterval = null; }
        this.carregarListas();
        this.cdr.detectChanges();
        if (tarefa.doRedmine) this.enviarAoRedmine(tarefa);
      },
      error: () => {
        this.finalizando = false;
        this.exibirMensagem('Erro ao finalizar tarefa.', 'erro');
      }
    });
  }

  /**
   * Grava as horas (média dos players) no Tempo estimado do chamado e, se configurados, os pontos e as horas de
   * teste. Separado da finalização: se o Redmine falhar, a tarefa continua finalizada e o moderador vê o motivo.
   */
  private enviarAoRedmine(tarefa: ITask): void {
    this.taskService.enviarRedmineSala(tarefa.id.toString()).subscribe({
      next: (envio) => {
        const gravados = envio.gravados.length ? `: ${envio.gravados.join(' · ')}` : '';
        if (envio.avisos.length) {
          this.exibirMensagem(`Redmine #${envio.chamado}${gravados}. Atenção: ${envio.avisos.join(' ')}`, 'erro', 15000);
        } else {
          this.exibirMensagem(`Estimativa gravada no Redmine #${envio.chamado}${gravados}`, 'sucesso');
        }
      },
      error: (err) => {
        this.exibirMensagem(`Tarefa finalizada, mas não foi enviada ao Redmine: `
          + (mensagemDeErro(err?.error) ?? `erro ${err?.status ?? ''}`), 'erro', 15000);
      }
    });
  }

  pularVotacao(): void {
    if (!this.tarefaEmVotacao) return;
    this.taskService.pularTarefaSala(this.tarefaEmVotacao.id.toString()).subscribe({
      next: () => {
        this.tarefaEmVotacao = null;
        this.estimativas = [];
        this.estimativasTeste = [];
        this.todosTestadoresVotaram = false;
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
            pontos_finais: t.pontosFinais ?? '',
            horas_finais: t.horasFinais ?? '',
            horas_teste_finais: t.horasTesteFinais ?? '',
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
        this.estimativasTeste = [];
        this.todosTestadoresVotaram = false;
        this.carregarTarefaEmVotacao();
        this.carregarResumoVotos(this.tarefaEmVotacao!.id, () => this.verificarLiberacoes(this.tarefaEmVotacao!.id));
      },
      error: () => this.exibirMensagem('Erro ao iniciar nova rodada.', 'erro')
    });
  }

  get mediaHorasTeste(): number | null {
    const valores = this.estimativasTeste
      .map((e: any) => e.horasTeste)
      .filter((h: any): h is number => h !== null && h !== undefined && typeof h === 'number' && h > 0);
    if (!valores.length) return null;
    return Math.round(valores.reduce((a, b) => a + b, 0) / valores.length * 10) / 10;
  }

  atualizarEstimativasTeste(): void {
    if (!this.tarefaEmVotacao) return;
    this.estimationService.listarTeste(this.tarefaEmVotacao.id.toString()).subscribe({
      next: (res) => {
        this.estimativasTeste = res;
        this.estimationService.todosTestadoresVotaram(this.tarefaEmVotacao!.id.toString()).subscribe({
          next: (todos) => { this.todosTestadoresVotaram = todos; this.cdr.detectChanges(); },
          error: () => {}
        });
        this.cdr.detectChanges();
      },
      error: () => {}
    });
  }

  liberarHorasTeste(): void {
    if (!this.tarefaEmVotacao) return;
    this.taskService.liberarHorasTesteSala(this.tarefaEmVotacao.id.toString()).subscribe({
      next: () => {
        this.carregarTarefaEmVotacao();
        this.exibirMensagem('Votação de teste liberada!', 'sucesso');
      },
      error: () => this.exibirMensagem('Erro ao liberar votação de teste.', 'erro')
    });
  }

  revelarHorasTeste(): void {
    if (!this.tarefaEmVotacao) return;
    this.estimationService.revelarHorasTeste(this.tarefaEmVotacao.id.toString()).subscribe({
      next: () => {
        this.atualizarEstimativasTeste();
        this.carregarTarefaEmVotacao();
      },
      error: () => this.exibirMensagem('Erro ao revelar horas de teste.', 'erro')
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

  get linkSala(): string | null {
    const codigo = this.salaService.getSalaCodigo();
    return codigo ? this.salaService.getLinkCompartilhavel(codigo) : null;
  }

  copiarLink(): void {
    const link = this.linkSala;
    if (!link) return;
    navigator.clipboard.writeText(link).then(() => {
      this.linkCopiado = true;
      this.cdr.detectChanges();
      setTimeout(() => { this.linkCopiado = false; this.cdr.detectChanges(); }, 2000);
    });
  }

  get salaIdNumero(): number | null {
    const salaId = Number(this.salaService.getSalaId());
    return Number.isFinite(salaId) && salaId > 0 ? salaId : null;
  }

  /*
   * Estatísticas e configuração do Redmine abrem num painel, sem sair da tela: navegar para outra rota fechava o
   * WebSocket do moderador e o backend inativava a sala em poucos segundos (e só a reativava na volta se ela já
   * tivesse tarefas pendentes).
   */

  /** Abre as estatísticas já filtradas pela sala atual (se houver). */
  irParaEstatisticas(): void {
    this.vendoEstatisticas = true;
  }

  irParaRedmine(): void {
    this.configurandoRedmine = true;
  }

  /** Importa os chamados da consulta configurada em /redmine; o backend busca com o acesso de lá. */
  importarDoRedmine(): void {
    if (this.importandoRedmine) return;
    this.importandoRedmine = true;
    this.cdr.detectChanges();
    this.taskService.importarRedmine().subscribe({
      next: (res: any) => {
        this.exibirMensagem(res?.message ?? 'Chamados importados.', 'sucesso');
        this.tarefaSelecionadaId = null;
        this.importandoRedmine = false;
        this.carregarListas();
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        this.exibirMensagem(mensagemDeErro(err?.error) ?? `Erro ${err?.status ?? ''}: falha ao importar do Redmine.`, 'erro');
        this.importandoRedmine = false;
        this.cdr.detectChanges();
      }
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
