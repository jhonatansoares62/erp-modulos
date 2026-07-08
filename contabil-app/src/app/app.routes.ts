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
      {
        path: 'plano-contas',
        loadComponent: () =>
          import('./features/plano-contas/plano-contas.component').then((m) => m.ConfigPlanoContasTabComponent),
      },
      {
        path: 'roteiros',
        loadComponent: () =>
          import('./features/roteiros/roteiros.component').then((m) => m.ConfigRoteirosTabComponent),
      },
      {
        path: 'pendencias',
        loadComponent: () =>
          import('./features/pendencias/pendencias.component').then((m) => m.ConfigPendenciasTabComponent),
      },
      {
        path: 'inventario',
        loadComponent: () =>
          import('./features/inventario/inventario.component').then((m) => m.ConfigInventarioTabComponent),
      },
      {
        path: 'abertura',
        loadComponent: () =>
          import('./features/abertura/abertura.component').then((m) => m.ConfigAberturaTabComponent),
      },
      {
        path: 'fiscal',
        loadComponent: () =>
          import('./features/fiscal/fiscal.component').then((m) => m.ConfigFiscalTabComponent),
      },
      {
        path: 'das',
        loadComponent: () => import('./features/das/das.component').then((m) => m.DasComponent),
      },
      { path: '', pathMatch: 'full', redirectTo: 'relatorios' },
    ],
  },
  { path: '**', redirectTo: '' },
];
