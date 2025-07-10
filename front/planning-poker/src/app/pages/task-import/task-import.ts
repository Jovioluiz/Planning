import { Component, OnInit} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

import * as Papa from 'papaparse';

@Component({
  selector: 'app-task-import',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './task-import.html',
  styleUrls: ['./task-import.scss']
})
export class TaskImport implements OnInit {
  tarefas: any[] = [];
  url = 'http://localhost:8081/api/tasks';

  constructor(private http: HttpClient, private router: Router){

  }

  ngOnInit(): void {
      this.carregarTarefas();
  }

  carregarTarefas() {
    this.http.get<any[]>(this.url).subscribe(data => {
      this.tarefas = data.filter(t => !t.estimada);
    });
  }

  handleFile(event: any) {
    const file = event.target.files[0];
    if (!file) return;

    Papa.parse(file, {
      header: true,
      skipEmptyLines: true,
      complete: (result: Papa.ParseResult<any>) => {
        const dados = result.data.filter(t => t.numero && t.titulo && t.descricao);
        this.http.post(this.url + '/importar', dados).subscribe(() => {
          this.carregarTarefas();
        });
      }
    });
  }

  iniciarEstimativa(tarefa: any) {
    this.router.navigate(['/estimativas', tarefa.id]);
  }

}
