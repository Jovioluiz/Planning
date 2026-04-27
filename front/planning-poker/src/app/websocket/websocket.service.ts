import { Inject, Injectable, OnDestroy, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { environment } from '../../environments/environment';

interface PendingSubscription {
  topic: string;
  callback: (message: IMessage) => void;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client!: Client;
  private activeSubscriptions: Map<string, StompSubscription> = new Map();
  private pendingSubscriptions: PendingSubscription[] = [];
  private readonly isBrowser: boolean;

  constructor(@Inject(PLATFORM_ID) platformId: object) {
    this.isBrowser = isPlatformBrowser(platformId);
    if (!this.isBrowser) return;

    this.client = new Client({
      webSocketFactory: () => new SockJS(`${environment.apiUrl}/ws-planning`),
      reconnectDelay: 5000,

      beforeConnect: () => {
        const token = sessionStorage.getItem('token');
        if (token) {
          this.client.connectHeaders = { Authorization: `Bearer ${token}` };
        }
      },

      onConnect: () => {
        for (const pending of this.pendingSubscriptions) {
          this.doSubscribe(pending.topic, pending.callback);
        }
        this.pendingSubscriptions = [];
      }
    });

    // Ativa imediatamente apenas se já há token (reload de página com sessão ativa)
    const token = sessionStorage.getItem('token');
    if (token) {
      this.client.activate();
    }
  }

  connect(): void {
    if (!this.isBrowser || !this.client || this.client.active) return;
    this.client.activate();
  }

  disconnect(): void {
    if (!this.isBrowser || !this.client) return;
    this.client.deactivate();
    this.activeSubscriptions.clear();
    this.pendingSubscriptions = [];
  }

  subscribe(topic: string, callback: (message: IMessage) => void): void {
    if (!this.isBrowser) return;
    if (this.client.connected) {
      this.doSubscribe(topic, callback);
    } else {
      this.pendingSubscriptions.push({ topic, callback });
    }
  }

  unsubscribe(topic: string): void {
    if (!this.isBrowser) return;
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
    this.disconnect();
  }
}
