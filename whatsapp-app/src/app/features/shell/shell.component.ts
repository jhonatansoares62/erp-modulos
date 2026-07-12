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
        <i class="pi pi-whatsapp"></i>
        <div class="brand-txt">
          <strong>ERPKit WhatsApp</strong>
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
      <a routerLink="/conversas" routerLinkActive="ativo"><i class="pi pi-comments"></i> Conversas</a>
      <a routerLink="/metricas" routerLinkActive="ativo"><i class="pi pi-chart-bar"></i> Métricas</a>
      <a routerLink="/configuracao" routerLinkActive="ativo"><i class="pi pi-cog"></i> Configuração</a>
      <a routerLink="/testes" routerLinkActive="ativo"><i class="pi pi-send"></i> Testes</a>
    </nav>

    <main class="conteudo">
      <router-outlet />
    </main>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100vh; background: var(--surface-ground); }
    /* Só a aba Conversas trava a altura na viewport (chat rola por dentro). As demais abas
       ficam em min-height:100vh e crescem com o conteúdo → rolagem normal da página + topbar sticky. */
    :host:has(app-conversas) { height: 100vh; height: 100dvh; }
    .topbar { flex: none; position: sticky; top: 0; z-index: 10; display: flex; align-items: center;
      justify-content: space-between; height: 4rem; padding: 0 1.5rem;
      background: var(--color-slate); color: #fff; box-shadow: 0 1px 0 rgba(0,0,0,.15); }
    .brand { display: flex; align-items: center; gap: .7rem; }
    .brand i { font-size: 1.5rem; color: var(--primary-color); }
    .brand-txt { display: flex; flex-direction: column; line-height: 1.15; }
    .brand-txt strong { font-family: var(--font-display); font-size: 1.15rem; letter-spacing: .02em; text-transform: uppercase; }
    .brand-txt span { font-size: .72rem; opacity: .7; letter-spacing: .04em; }
    .user { display: flex; align-items: center; gap: .75rem; }
    .quem { font-size: .85rem; opacity: .9; display: inline-flex; align-items: center; gap: .35rem; }
    :host ::ng-deep .user .p-button.p-button-text { color: #fff; }

    .tabs { flex: none; display: flex; flex-wrap: wrap; gap: .25rem; padding: 0 1.5rem; background: var(--color-slate-mid);
      box-shadow: inset 0 -1px 0 rgba(255,255,255,.08); }
    .tabs-sep { width: 1px; align-self: center; height: 1.3rem; background: rgba(255,255,255,.18); margin: 0 .5rem; }
    .tabs a { display: inline-flex; align-items: center; gap: .45rem; padding: .8rem 1rem;
      color: rgba(255,255,255,.75); font-size: .9rem; font-weight: 500;
      border-bottom: 3px solid transparent; transition: color .15s, border-color .15s; }
    .tabs a:hover { color: #fff; }
    .tabs a.ativo { color: #fff; border-bottom-color: var(--primary-color); }
    .tabs a i { font-size: .95rem; }

    /* width:100% pra preencher até 1100px (como bloco) — sem isso, como flex-item de coluna com
       margin auto, encolhe pro tamanho do conteúdo. O :has(app-conversas) abaixo tira o teto. */
    .conteudo { flex: 1; min-height: 0; width: 100%; max-width: 1100px; margin: 0 auto; padding: 1.75rem 1.5rem 3rem; }
    /* Full-bleed só na aba Conversas (chat ocupa a tela toda); as demais seguem em 1100px centrado.
       display:flex faz o app-conversas preencher a altura via flex (sem depender de % — a coluna
       do shell é min-height, logo não é "altura definida" pra resolver height:100%). */
    .conteudo:has(app-conversas) { max-width: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
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
