import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { ImportarTarefas } from './importar-tarefas/importar-tarefas';
import { Aguardando } from './pages/aguardando/aguardando';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
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
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' }
];
