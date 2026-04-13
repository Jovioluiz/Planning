import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { TaskService } from '../../services/task.service';

@Component({
  selector: 'app-aguardando',
  standalone: true,
  templateUrl: './aguardando.html',
  styleUrls: ['./aguardando.scss']
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
