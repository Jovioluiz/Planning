import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { environment } from '../../environments/environment';

interface PendingSubscription {
  topic: string;
  callback: (message: IMessage) => void;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client: Client;
  // Assinaturas ativas (quando já conectado)
  private activeSubscriptions: Map<string, StompSubscription> = new Map();
  // Fila de assinaturas aguardando conexão
  private pendingSubscriptions: PendingSubscription[] = [];

  constructor() {
    this.client = new Client({
      webSocketFactory: () => new SockJS(`${environment.apiUrl}/ws-planning`),
      reconnectDelay: 5000,
      onConnect: () => {
        // Registra todas as assinaturas pendentes após conectar (ou reconectar)
        for (const pending of this.pendingSubscriptions) {
          this.doSubscribe(pending.topic, pending.callback);
        }
      }
    });
    this.client.activate();
  }

  subscribe(topic: string, callback: (message: IMessage) => void): void {
    if (this.client.connected) {
      this.doSubscribe(topic, callback);
    } else {
      // Adiciona à fila — será registrado assim que a conexão for estabelecida
      this.pendingSubscriptions.push({ topic, callback });
    }
  }

  unsubscribe(topic: string): void {
    const sub = this.activeSubscriptions.get(topic);
    if (sub) {
      sub.unsubscribe();
      this.activeSubscriptions.delete(topic);
    }
    this.pendingSubscriptions = this.pendingSubscriptions.filter(p => p.topic !== topic);
  }

  private doSubscribe(topic: string, callback: (message: IMessage) => void): void {
    // Evita assinaturas duplicadas no mesmo tópico
    if (this.activeSubscriptions.has(topic)) {
      this.activeSubscriptions.get(topic)!.unsubscribe();
    }
    const sub = this.client.subscribe(topic, callback);
    this.activeSubscriptions.set(topic, sub);
  }

  ngOnDestroy(): void {
    this.client.deactivate();
  }
}
