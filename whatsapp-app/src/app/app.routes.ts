import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () => import('./features/shell/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'conversas',
        loadComponent: () =>
          import('./features/conversas/conversas.component').then((m) => m.ConversasComponent),
      },
      {
        path: 'metricas',
        loadComponent: () =>
          import('./features/metricas/metricas.component').then((m) => m.MetricasComponent),
      },
      {
        path: 'configuracao',
        loadComponent: () =>
          import('./features/configuracao/configuracao.component').then((m) => m.ConfiguracaoComponent),
      },
      {
        path: 'testes',
        loadComponent: () =>
          import('./features/testes/testes.component').then((m) => m.TestesComponent),
      },
      { path: '', pathMatch: 'full', redirectTo: 'conversas' },
    ],
  },
  { path: '**', redirectTo: '' },
];
