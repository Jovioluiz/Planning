import { Component } from '@angular/core';
import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { CommonModule } from '@angular/common';
import { TaskService } from '../../services/task.service';
import { firstValueFrom } from 'rxjs/internal/firstValueFrom';

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

  constructor(private auth: AuthService, private router: Router, private taskService: TaskService) {}

  async login(event: Event) {
      event.preventDefault();
      this.erro = '';
      
      if (!this.usuario || !this.senha || !this.perfil) {
        this.erro = 'Preencha todos os campos';
        return;
      }

      console.log('Usuario:', this.usuario);
      try {
        const response = await this.auth.login(this.usuario, this.senha, this.perfil).toPromise();
        if (response) {
          console.log('response:', response);
          console.log('perfil:', this.perfil);
          if (this.perfil == 'ADMIN'){
            this.router.navigate(['/importar']);
          } else {
            // this.getTarefaLiberada();
            console.log('Tarefas Liberadas: ', this.getTarefaLiberada());
            const id = await this.getTarefaLiberada();

            console.log('ID da tarefa liberada:', id);
            if (id) {
              this.router.navigate(['/estimativas/'+id]);
            } else {
              this.router.navigate(['/estimativas/0']);
              //this.erro = 'Nenhuma tarefa liberada encontrada!';
            }
          }
        } else {
          this.erro = 'Usuário ou senha inválidos';
        }
      } catch (e) {
        console.error('Erro no login', e);
        this.erro = 'Erro ao conectar com o servidor';
      }
    }

    async getTarefaLiberada(): Promise<string | null> {
      try {
        const res: ITask[] = await firstValueFrom(this.taskService.getTarefasLiberadas());
        const tarefa = res.length > 0 ? res[0] : null;
        return tarefa?.id?.toString() || null;
      } catch (err) {
        console.error('Erro ao buscar tarefa liberada', err);
        return null;
      }
    }

  }
