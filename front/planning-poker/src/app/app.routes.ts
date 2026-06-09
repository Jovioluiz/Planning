import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { ImportarTarefas } from './importar-tarefas/importar-tarefas';
import { Aguardando } from './pages/aguardando/aguardando';
import { GerenciarUsuarios } from './pages/gerenciar-usuarios/gerenciar-usuarios';
import { Salas } from './pages/salas/salas';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
  {
    path: 'sala/:codigo',
    loadComponent: () => import('./pages/sala-landing/sala-landing').then(m => m.SalaLanding)
  },
  {
    path: 'salas',
    component: Salas,
    canActivate: [authGuard]
  },
  {
    path: 'usuarios',
    component: GerenciarUsuarios,
    canActivate: [authGuard]
  },
  {
    path: 'importar',
    component: ImportarTarefas,
    canActivate: [authGuard]
  },
  {
    path: 'aguardando',
    component: Aguardando,
    canActivate: [authGuard]
  },
  {
    path: 'estimativas/:id',
    loadComponent: () =>
      import('./pages/estimation-board/estimation-board').then(m => m.EstimationBoard),
    canActivate: [authGuard]
  },
  { path: 'selecionar-sprint', redirectTo: 'aguardando', pathMatch: 'full' },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' }
];
