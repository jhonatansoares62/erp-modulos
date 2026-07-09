import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="topbar">
      <div class="brand">
        <i class="pi pi-calculator"></i>
        <div class="brand-txt">
          <strong>ERPKit Contabilidade</strong>
          @if (auth.user()?.empresa; as empresa) {
            <span>{{ empresa }}</span>
          }
        </div>
      </div>
      <div class="user">
        <span class="quem"><i class="pi pi-user"></i> {{ auth.user()?.nome }}</span>
        <p-button label="Sair" icon="pi pi-sign-out" severity="secondary" [text]="true" (onClick)="sair()" />
      </div>
    </header>

    <nav class="tabs">
      <a routerLink="/relatorios" routerLinkActive="ativo"><i class="pi pi-chart-bar"></i> Relatórios</a>
      <a routerLink="/razao" routerLinkActive="ativo"><i class="pi pi-book"></i> Razão</a>
      <a routerLink="/diario" routerLinkActive="ativo"><i class="pi pi-list"></i> Livro Diário</a>
      <span class="tabs-sep"></span>
      <a routerLink="/plano-contas" routerLinkActive="ativo"><i class="pi pi-sitemap"></i> Plano de Contas</a>
      <a routerLink="/roteiros" routerLinkActive="ativo"><i class="pi pi-bolt"></i> Roteiros</a>
      <a routerLink="/pendencias" routerLinkActive="ativo"><i class="pi pi-inbox"></i> Pendências</a>
      <a routerLink="/inventario" routerLinkActive="ativo"><i class="pi pi-box"></i> Inventário</a>
      <a routerLink="/abertura" routerLinkActive="ativo"><i class="pi pi-flag"></i> Abertura</a>
      <a routerLink="/fiscal" routerLinkActive="ativo"><i class="pi pi-percentage"></i> Fiscal</a>
      <a routerLink="/das" routerLinkActive="ativo"><i class="pi pi-money-bill"></i> DAS</a>
      <a routerLink="/encerramento" routerLinkActive="ativo"><i class="pi pi-lock"></i> Encerramento</a>
    </nav>

    <main class="conteudo">
      <router-outlet />
    </main>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; background: var(--surface-ground); }
    .topbar { position: sticky; top: 0; z-index: 10; display: flex; align-items: center;
      justify-content: space-between; height: 4rem; padding: 0 1.5rem;
      background: var(--color-slate); color: #fff; box-shadow: 0 1px 0 rgba(0,0,0,.15); }
    .brand { display: flex; align-items: center; gap: .7rem; }
    .brand i { font-size: 1.5rem; color: var(--color-copper-light); }
    .brand-txt { display: flex; flex-direction: column; line-height: 1.15; }
    .brand-txt strong { font-family: var(--font-display); font-size: 1.15rem; letter-spacing: .02em; text-transform: uppercase; }
    .brand-txt span { font-size: .72rem; opacity: .7; letter-spacing: .04em; }
    .user { display: flex; align-items: center; gap: .75rem; }
    .quem { font-size: .85rem; opacity: .9; display: inline-flex; align-items: center; gap: .35rem; }
    :host ::ng-deep .user .p-button.p-button-text { color: #fff; }

    .tabs { display: flex; flex-wrap: wrap; gap: .25rem; padding: 0 1.5rem; background: var(--color-slate-mid);
      box-shadow: inset 0 -1px 0 rgba(255,255,255,.08); }
    .tabs-sep { width: 1px; align-self: center; height: 1.3rem; background: rgba(255,255,255,.18); margin: 0 .5rem; }
    .tabs a { display: inline-flex; align-items: center; gap: .45rem; padding: .8rem 1rem;
      color: rgba(255,255,255,.75); font-size: .9rem; font-weight: 500;
      border-bottom: 3px solid transparent; transition: color .15s, border-color .15s; }
    .tabs a:hover { color: #fff; }
    .tabs a.ativo { color: #fff; border-bottom-color: var(--color-copper-light); }
    .tabs a i { font-size: .95rem; }

    .conteudo { max-width: 1100px; margin: 0 auto; padding: 1.75rem 1.5rem 3rem; }
  `],
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  private router = inject(Router);

  sair(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
