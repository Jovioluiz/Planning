import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { environment } from '../../environments/environment';
import { AuthService } from '../services/auth.service'; // Importar o serviço

interface PendingSubscription {
  topic: string;
  callback: (message: IMessage) => void;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client: Client;
  private activeSubscriptions: Map<string, StompSubscription> = new Map();
  private pendingSubscriptions: PendingSubscription[] = [];

  // Injetar o AuthService
  constructor(private authService: AuthService) {
    this.client = new Client({
      webSocketFactory: () => new SockJS(`${environment.apiUrl}/ws-planning`),
      reconnectDelay: 5000,
      
      // NOVO: Adiciona o token antes de tentar conectar via STOMP
      beforeConnect: () => {
        const token = this.authService.getToken();
        if (token) {
          this.client.connectHeaders = {
            Authorization: `Bearer ${token}`
          };
        }
      },

      onConnect: () => {
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