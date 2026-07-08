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
        path: 'relatorios',
        loadComponent: () =>
          import('./features/relatorios/relatorios.component').then((m) => m.RelatoriosComponent),
      },
      {
        path: 'razao',
        loadComponent: () => import('./features/razao/razao.component').then((m) => m.RazaoComponent),
      },
      {
        path: 'diario',
        loadComponent: () => import('./features/diario/diario.component').then((m) => m.DiarioComponent),
      },
      { path: '', pathMatch: 'full', redirectTo: 'relatorios' },
    ],
  },
  { path: '**', redirectTo: '' },
];
