import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { TaskService } from '../../services/task.service';

@Component({
  selector: 'app-selecionar-sprint',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './selecionar-sprint.html',
  styleUrls: ['./selecionar-sprint.scss']
})
export class SelecionarSprint implements OnInit {
  sprints: string[] = [];
  carregando = true;
  selecionando = false;
  erro = '';
  usuario: string | null = '';

  constructor(
    private auth: AuthService,
    private taskService: TaskService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.auth.isJogador()) {
      this.router.navigate(['/importar']);
      return;
    }
    this.usuario = this.auth.getUsuario();
    this.taskService.getSprints().subscribe({
      next: (res) => { this.sprints = res; this.carregando = false; },
      error: () => { this.erro = 'Erro ao carregar sprints.'; this.carregando = false; }
    });
  }

  selecionar(sprint: string): void {
    this.selecionando = true;
    this.auth.selecionarSprint(sprint).subscribe({
      next: () => {
        this.taskService.getTarefasLiberadas().subscribe({
          next: (tarefas) => {
            if (tarefas.length > 0) {
              this.router.navigate(['/estimativas', tarefas[0].id]);
            } else {
              this.router.navigate(['/aguardando']);
            }
          },
          error: () => this.router.navigate(['/aguardando'])
        });
      },
      error: () => {
        this.erro = 'Erro ao selecionar sprint. Tente novamente.';
        this.selecionando = false;
      }
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
