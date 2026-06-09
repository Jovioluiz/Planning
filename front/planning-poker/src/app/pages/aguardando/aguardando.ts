import { ChangeDetectorRef, Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { TaskService } from '../../services/task.service';
import { WebSocketService } from '../../websocket/websocket.service';

@Component({
  selector: 'app-aguardando',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './aguardando.html',
  styleUrls: ['./aguardando.scss']
})
export class Aguardando implements OnInit, OnDestroy {
  usuario: string | null = '';
  salaEncerrada = false;
  private topicSessoes = '/topic/sessoes';
  private intervalo: ReturnType<typeof setInterval> | null = null;

  constructor(
    private router: Router,
    private auth: AuthService,
    private taskService: TaskService,
    private wsService: WebSocketService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.usuario = this.auth.getUsuario();
    const salaId = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('salaId') : null;
    if (salaId) {
      this.topicSessoes = `/topic/sala/${salaId}/sessoes`;
    }
    this.wsService.subscribe(this.topicSessoes, (msg) => {
      const data = JSON.parse(msg.body);
      if (data.acao === 'SALA_INATIVADA') {
        if (this.intervalo) { clearInterval(this.intervalo); this.intervalo = null; }
        if (typeof sessionStorage !== 'undefined') {
          sessionStorage.removeItem('salaId');
          sessionStorage.removeItem('salaCodigo');
          sessionStorage.removeItem('salaNome');
        }
        this.salaEncerrada = true;
        this.cdr.detectChanges();
        setTimeout(() => this.router.navigate(['/login']), 3000);
      }
    });
    this.verificar();
    this.intervalo = setInterval(() => this.verificar(), 5000);
  }

  ngOnDestroy(): void {
    if (this.intervalo) clearInterval(this.intervalo);
    this.wsService.unsubscribe(this.topicSessoes);
  }

  private verificar(): void {
    this.taskService.getTarefasLiberadasSala().subscribe({
      next: (tarefas) => {
        if (tarefas.length > 0) {
          this.router.navigate(['/estimativas', tarefas[0].id]);
        }
      },
      error: () => {}
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
