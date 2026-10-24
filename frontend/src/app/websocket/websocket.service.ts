import { Inject, Injectable, OnDestroy, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { environment } from '../../environments/environment';
import { EventoSala, paraMensagemAntiga } from '../services/api-jakarta';

/** Mesmo formato do IMessage do STOMP que os componentes já usavam: { body: string }. */
export interface MensagemTopico {
  body: string;
}

type Callback = (mensagem: MensagemTopico) => void;

/**
 * WebSocket nativo do planning-jakarta (ws://.../planning/ws/salas/{salaId}?token=...), no lugar do STOMP/SockJS
 * do backend Spring. Para os componentes nada muda: eles assinam os "tópicos" antigos e recebem mensagens no
 * formato antigo ({ acao, taskId, ... }); a tradução fica em {@link paraMensagemAntiga}.
 *
 * <ul>
 *   <li>Uma conexão por sala: assinar /topic/sala/{id}/... conecta nessa sala. Estar conectado é o que faz o
 *       usuário aparecer como online (não há mais POST de presença).</li>
 *   <li>Reconecta a cada 5s se cair (como o reconnectDelay do STOMP) e manda "ping" a cada 30s.</li>
 *   <li>Fechamento 1008: token inválido/expirado ou usuário desativado; não adianta reconectar.</li>
 *   <li>Os tópicos globais do original (/topic/estimativas, /topic/sessoes) eram das tarefas sem sala, que não
 *       existem mais: assiná-los não faz nada.</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private readonly isBrowser: boolean;
  private socket: WebSocket | null = null;
  private salaConectada: string | null = null;
  private readonly assinaturas = new Map<string, Callback>();
  private online: string[] = [];
  private reconexao: ReturnType<typeof setTimeout> | null = null;
  private ping: ReturnType<typeof setInterval> | null = null;
  private fechamentoAdiado: ReturnType<typeof setTimeout> | null = null;
  private encerrado = false;

  constructor(@Inject(PLATFORM_ID) platformId: object) {
    this.isBrowser = isPlatformBrowser(platformId);
  }

  /** Mantido por compatibilidade: a conexão abre ao assinar um tópico de sala. */
  connect(): void {
    // nada: a sala define a conexão
  }

  disconnect(): void {
    this.assinaturas.clear();
    if (this.fechamentoAdiado) { clearTimeout(this.fechamentoAdiado); this.fechamentoAdiado = null; }
    this.fechar();
  }

  subscribe(topic: string, callback: Callback): void {
    if (!this.isBrowser) return;
    this.assinaturas.set(topic, callback);
    const salaId = this.salaDoTopico(topic);
    if (salaId && salaId !== this.salaConectada) {
      this.conectar(salaId);
    }
  }

  unsubscribe(topic: string): void {
    if (!this.isBrowser) return;
    this.assinaturas.delete(topic);
    // Trocar de tela dentro da sala destrói um componente antes de criar o outro: fechar na hora faria o backend
    // ver uma saída e uma entrada (e até disparar um "todos votaram" cedo). Espera um pouco antes de fechar.
    if (this.fechamentoAdiado) clearTimeout(this.fechamentoAdiado);
    this.fechamentoAdiado = setTimeout(() => {
      this.fechamentoAdiado = null;
      const aindaNaSala = [...this.assinaturas.keys()].some(t => this.salaDoTopico(t) === this.salaConectada);
      if (!aindaNaSala) {
        this.fechar();
      }
    }, 3000);
  }

  private conectar(salaId: string): void {
    this.fechar();
    const token = sessionStorage.getItem('token');
    if (!token) return;
    this.encerrado = false;
    this.salaConectada = salaId;
    const base = environment.apiUrl.replace(/^http/, 'ws');
    const socket = new WebSocket(`${base}/ws/salas/${salaId}?token=${encodeURIComponent(token)}`);
    this.socket = socket;

    socket.onmessage = (evento) => {
      if (evento.data === 'pong') return;
      let dados: EventoSala;
      try {
        dados = JSON.parse(evento.data);
      } catch {
        return;
      }
      for (const { topico, dados: mensagem } of paraMensagemAntiga(dados, this.online)) {
        this.entregar(`/topic/sala/${salaId}/${topico}`, mensagem);
      }
      if (dados.tipo === 'PRESENCA' && dados.online) {
        this.online = dados.online;
      }
    };
    socket.onopen = () => {
      this.ping = setInterval(() => socket.readyState === WebSocket.OPEN && socket.send('ping'), 30_000);
    };
    socket.onclose = (evento) => {
      if (this.ping) { clearInterval(this.ping); this.ping = null; }
      if (this.socket !== socket || this.encerrado) return;
      this.socket = null;
      // 1008: recusado pelo servidor (token/usuário). Reconectar só repetiria a recusa.
      if (evento.code !== 1008) {
        this.reconexao = setTimeout(() => this.salaConectada === salaId && this.conectar(salaId), 5000);
      }
    };
  }

  private entregar(topico: string, dados: any): void {
    const callback = this.assinaturas.get(topico);
    if (callback) {
      callback({ body: JSON.stringify(dados) });
    }
  }

  private fechar(): void {
    this.encerrado = true;
    if (this.reconexao) { clearTimeout(this.reconexao); this.reconexao = null; }
    if (this.ping) { clearInterval(this.ping); this.ping = null; }
    if (this.socket) {
      this.socket.close(1000, 'saiu da sala');
      this.socket = null;
    }
    this.salaConectada = null;
    this.online = [];
  }

  private salaDoTopico(topic: string): string | null {
    const encontrado = /^\/topic\/sala\/(\d+)\//.exec(topic);
    return encontrado ? encontrado[1] : null;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
