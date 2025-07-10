import { RouterModule, Routes } from '@angular/router';
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';

import { AppComponent } from './app';
import { Login } from './pages/login/login';
import { TaskImport } from './pages/task-import/task-import';
import { EstimationBoard } from './pages/estimation-board/estimation-board';

import { AuthService } from './services/auth.service';
import { TaskService } from './services/task.service';
import { EstimationService } from './services/estimation.service';
import { ImportarTarefas } from './importar-tarefas/importar-tarefas';

import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
  // { path: 'importar', component: TaskImport, canActivate: [authGuard] },
  { path: 'importar', component: ImportarTarefas, canActivate: [authGuard] },
  { path: 'estimativas/:id', loadComponent: () => import('./pages/estimation-board/estimation-board').then(m => m.EstimationBoard), canActivate: [authGuard]  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];


@NgModule({
  //declarations: [
 //   AppComponent,
 //   Login
//     TaskImport,
//     EstimationBoard
 // ],
  imports: [
    BrowserModule,
    FormsModule,
    HttpClientModule,
    RouterModule.forRoot(routes)
  ],
  providers: [
    AuthService,
    TaskService,
    EstimationService
  ],
  //bootstrap: [AppComponent]
})
export class AppModule { }

