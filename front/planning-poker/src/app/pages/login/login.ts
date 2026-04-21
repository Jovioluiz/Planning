import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';
import { TaskService } from '../../services/task.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterModule, CommonModule],
  templateUrl: './login.html',
  styleUrls: ['./login.scss']
})
export class Login {
  usuario = '';
  senha = '';
  perfil = '';
  erro = '';
  carregando = false;

  constructor(
    private auth: AuthService,
    private router: Router,
    private taskService: TaskService
  ) {}

  login(event: Event): void {
    event.preventDefault();
    this.erro = '';

    if (!this.usuario || !this.senha || !this.perfil) {
      this.erro = 'Preencha todos os campos';
      return;
    }

    this.carregando = true;

    this.auth.login(this.usuario, this.senha, this.perfil).subscribe({
      next: (sucesso) => {
        if (!sucesso) {
          this.erro = 'Usuário ou senha inválidos';
          this.carregando = false;
          return;
        }

        if (this.auth.isAdmin()) {
          this.carregando = false;
          this.router.navigate(['/importar']);
          return;
        }

        if (this.auth.isJogador()) {
          this.carregando = false;
          this.router.navigate(['/selecionar-sprint']);
          return;
        }

        // OBSERVADOR: busca tarefa liberada diretamente
        this.taskService.getTarefasLiberadas().subscribe({
          next: (tarefas) => {
            this.carregando = false;
            if (tarefas.length > 0) {
              this.router.navigate(['/estimativas', tarefas[0].id]);
            } else {
              this.router.navigate(['/aguardando']);
            }
          },
          error: () => {
            this.carregando = false;
            this.router.navigate(['/aguardando']);
          }
        });
      },
      error: (err: Error) => {
        // Exibe a mensagem específica do backend (ex: bloqueio de ADMIN)
        this.erro = err.message || 'Erro ao conectar com o servidor';
        this.carregando = false;
      }
    });
  }
}
