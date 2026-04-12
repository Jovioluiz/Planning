import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { TaskService } from '../../services/task.service';

@Component({
  selector: 'app-aguardando',
  standalone: true,
  template: `
    <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;gap:16px;font-family:sans-serif;">
      <h2>Aguardando tarefa ser liberada...</h2>
      <p>O administrador ainda não liberou nenhuma tarefa para votação. Esta página atualiza automaticamente.</p>
      <button (click)="logout()" style="padding:8px 16px;cursor:pointer;">Sair</button>
    </div>
  `
})
export class Aguardando implements OnInit, OnDestroy {
  private intervalo: ReturnType<typeof setInterval> | null = null;

  constructor(
    private router: Router,
    private auth: AuthService,
    private taskService: TaskService
  ) {}

  ngOnInit(): void {
    this.verificar();
    this.intervalo = setInterval(() => this.verificar(), 5000);
  }

  ngOnDestroy(): void {
    if (this.intervalo) clearInterval(this.intervalo);
  }

  private verificar(): void {
    this.taskService.getTarefasLiberadas().subscribe({
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
