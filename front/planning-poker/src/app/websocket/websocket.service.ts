import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client: Client;

  constructor() {
    this.client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8081/ws-planning'),
      reconnectDelay: 5000
    });
    this.client.activate();
  }

  subscribe(topic: string, callback: (message: IMessage) => void) {
    this.client.onConnect = () => {
      this.client.subscribe(topic, callback);
    };
  }
}
