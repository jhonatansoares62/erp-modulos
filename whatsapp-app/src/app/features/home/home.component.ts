import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { CardModule } from 'primeng/card';

/**
 * Placeholder das telas futuras (Conversas, Métricas, Configuração, Testes). As 4 rotas
 * filhas apontam pra cá por enquanto; o título "Em construção" reflete a rota atual.
 * Na Fase 3 cada rota ganha seu componente real (a de Testes é a primeira a ser plugada).
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CardModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-card>
      <div class="placeholder">
        <i class="pi pi-wrench"></i>
        <h2>{{ titulo() }}</h2>
        <p>Em construção — esta tela chega numa próxima fase.</p>
      </div>
    </p-card>
  `,
  styles: [`
    .placeholder { display: flex; flex-direction: column; align-items: center; gap: .6rem;
      padding: 3rem 1rem; text-align: center; }
    .placeholder i { font-size: 2.5rem; color: var(--primary-color); }
    .placeholder h2 { font-family: var(--font-display); font-size: 1.5rem; text-transform: uppercase;
      letter-spacing: .02em; color: var(--text-color); }
    .placeholder p { color: var(--text-color-secondary); }
  `],
})
export class HomeComponent {
  private router = inject(Router);

  private readonly rota = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  private readonly titulos: Record<string, string> = {
    conversas: 'Conversas',
    metricas: 'Métricas',
    configuracao: 'Configuração',
    testes: 'Testes',
  };

  readonly titulo = computed(() => {
    const seg = (this.rota() ?? '').split('/').filter(Boolean)[0] ?? 'conversas';
    return this.titulos[seg] ?? 'WhatsApp';
  });
}
