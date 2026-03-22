import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { TaskService, ITask } from '../../services/task.service';

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

  async login(event: Event): Promise<void> {
    event.preventDefault();
    this.erro = '';

    if (!this.usuario || !this.senha || !this.perfil) {
      this.erro = 'Preencha todos os campos';
      return;
    }

    this.carregando = true;

    try {
      const response = await firstValueFrom(
        this.auth.login(this.usuario, this.senha, this.perfil)
      );

      if (response) {
        if (this.auth.isAdmin()) {
          this.router.navigate(['/importar']);
        } else {
          const id = await this.getTarefaLiberada();
          this.router.navigate(['/estimativas', id ?? '0']);
        }
      } else {
        this.erro = 'Usuário ou senha inválidos';
      }
    } catch {
      this.erro = 'Erro ao conectar com o servidor';
    } finally {
      this.carregando = false;
    }
  }

  private async getTarefaLiberada(): Promise<string | null> {
    try {
      const res = await firstValueFrom(this.taskService.getTarefasLiberadas());
      return res.length > 0 ? res[0].id.toString() : null;
    } catch {
      return null;
    }
  }
}
