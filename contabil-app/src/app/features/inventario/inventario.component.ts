import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { finalize } from 'rxjs';
import { PRIMENG_MODULES } from '../../shared/primeng';
import { BotaoComponent } from '../../shared/components/botao/botao.component';
import { ContabilidadeService, InventarioApuracao, InventarioBase } from '../../shared/services/contabilidade.service';

/**
 * Apuração de CMV por inventário periódico: informa a contagem física (Estoque Final) do
 * período e o sistema apura CMV = Estoque Inicial + Compras − Estoque Final, postando
 * D CMV / C Estoque. Reapurar o mesmo período substitui a apuração anterior (idempotente).
 */
@Component({
  selector: 'app-config-inventario-tab',
  standalone: true,
  imports: [FormsModule, ...PRIMENG_MODULES, BotaoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="hint">Inventário periódico: informe a contagem física do estoque no fim do período.
      O CMV é apurado por diferença (EI + Compras − EF) e lançado como D CMV / C Estoque.</p>

    <div class="inv-filtros">
      <div class="field">
        <label>De</label>
        <p-datepicker [(ngModel)]="de" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
      </div>
      <div class="field">
        <label>Até</label>
        <p-datepicker [(ngModel)]="ate" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
      </div>
      <app-botao label="Carregar base" icon="pi pi-calculator" severity="secondary"
                 [loading]="carregando()" (clicado)="carregarBase()" />
    </div>

    @if (base(); as b) {
      <div class="inv-base">
        <div class="linha"><span>Estoque Inicial (EI)</span><span>{{ reais(b.estoqueInicialCentavos) }}</span></div>
        <div class="linha"><span>(+) Compras do período</span><span>{{ reais(b.comprasCentavos) }}</span></div>
        <div class="linha total"><span>= Estoque disponível (EI + Compras)</span><span>{{ reais(b.estoqueDisponivelCentavos) }}</span></div>
      </div>

      <div class="inv-apurar">
        <div class="field">
          <label>Estoque Final contado (EF)</label>
          <p-inputnumber [(ngModel)]="estoqueFinalReais" mode="currency" currency="BRL" locale="pt-BR"
                         [min]="0" styleClass="w-full" />
        </div>
        <div class="preview">
          CMV previsto: <strong>{{ reais(cmvPrevistoCentavos()) }}</strong>
        </div>
        <app-botao label="Apurar CMV" icon="pi pi-check" [loading]="apurando()"
                   [disabled]="!efValido()" (clicado)="apurar()" />
      </div>
    }

    <div class="inv-hist">
      <h4>Apurações</h4>
      @if (historico().length) {
        <p-table [value]="historico()" styleClass="p-datatable-sm">
          <ng-template #header>
            <tr>
              <th>Período</th>
              <th style="text-align:right">EI</th>
              <th style="text-align:right">Compras</th>
              <th style="text-align:right">EF</th>
              <th style="text-align:right">CMV</th>
              <th>Situação</th>
            </tr>
          </ng-template>
          <ng-template #body let-a>
            <tr [class.inativa]="!a.ativo">
              <td>{{ a.de }} — {{ a.ate }}</td>
              <td style="text-align:right">{{ reais(a.estoqueInicialCentavos) }}</td>
              <td style="text-align:right">{{ reais(a.comprasCentavos) }}</td>
              <td style="text-align:right">{{ reais(a.estoqueFinalCentavos) }}</td>
              <td style="text-align:right">{{ reais(a.cmvCentavos) }}</td>
              <td>
                @if (a.ativo) { <p-tag value="Vigente" severity="success" /> }
                @else { <p-tag value="Substituída" severity="secondary" /> }
              </td>
            </tr>
          </ng-template>
        </p-table>
      } @else {
        <p class="vazio">Nenhuma apuração registrada.</p>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .hint { margin: 0 0 1.25rem; font-size: .85rem; color: var(--text-color-secondary); }
    .inv-filtros { display: flex; align-items: flex-end; gap: 1rem; margin-bottom: 1.5rem; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .inv-base { max-width: 30rem; margin-bottom: 1.5rem; }
    .inv-base .linha { display: flex; justify-content: space-between; padding: .4rem 0; border-bottom: 1px solid var(--surface-border); }
    .inv-base .total { font-weight: 700; }
    .inv-apurar { display: flex; align-items: flex-end; gap: 1rem; margin-bottom: 2rem; flex-wrap: wrap; }
    .preview { padding-bottom: .5rem; color: var(--text-color-secondary); }
    .inv-hist h4 { margin: 0 0 .75rem; }
    .vazio { color: var(--text-color-secondary); }
    tr.inativa { opacity: .55; }
    .w-full { width: 100%; }
  `],
})
export class ConfigInventarioTabComponent implements OnInit {
  private service = inject(ContabilidadeService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  carregando = signal(false);
  apurando = signal(false);
  base = signal<InventarioBase | null>(null);
  historico = signal<InventarioApuracao[]>([]);

  de: Date = new Date(new Date().getFullYear(), 0, 1);
  // "Até" = hoje (não 31/12): o CMV é datado no fim do período, então apurar até hoje
  // deixa o lançamento visível no relatório padrão (que também vai até hoje).
  ate: Date = new Date();
  estoqueFinalReais = 0;

  ngOnInit(): void {
    this.recarregarHistorico();
  }

  cmvPrevistoCentavos(): number {
    const disp = this.base()?.estoqueDisponivelCentavos ?? 0;
    return Math.max(0, disp - this.efCentavos());
  }

  efCentavos(): number {
    return Math.round((this.estoqueFinalReais ?? 0) * 100);
  }

  efValido(): boolean {
    const disp = this.base()?.estoqueDisponivelCentavos ?? 0;
    return this.efCentavos() >= 0 && this.efCentavos() <= disp;
  }

  carregarBase(): void {
    const de = this.iso(this.de);
    const ate = this.iso(this.ate);
    if (!de || !ate) {
      this.msg.add({ severity: 'warn', summary: 'Inventário', detail: 'Informe o período (de e até).' });
      return;
    }
    this.carregando.set(true);
    this.service.inventarioBase(de, ate).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.carregando.set(false)),
    ).subscribe({
      next: (b) => { this.base.set(b); this.estoqueFinalReais = 0; },
      error: () => this.msg.add({ severity: 'error', summary: 'Inventário', detail: 'Falha ao carregar a base do período.' }),
    });
  }

  apurar(): void {
    const b = this.base();
    if (!b) return;
    if (!this.efValido()) {
      this.msg.add({ severity: 'warn', summary: 'Inventário',
        detail: 'O Estoque Final não pode ser maior que EI + Compras (CMV negativo).' });
      return;
    }
    this.apurando.set(true);
    this.service.apurarCmv(b.de, b.ate, this.efCentavos()).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.apurando.set(false)),
    ).subscribe({
      next: (a) => {
        this.msg.add({ severity: 'success', summary: 'CMV apurado',
          detail: `CMV do período: ${this.reais(a.cmvCentavos)}.` });
        this.recarregarHistorico();
      },
      error: (err) => this.msg.add({ severity: 'error', summary: 'Inventário',
        detail: err?.error?.message || 'Falha ao apurar o CMV.' }),
    });
  }

  private recarregarHistorico(): void {
    this.service.listarInventario().pipe(
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (lista) => this.historico.set(lista ?? []),
      error: () => {},
    });
  }

  reais(centavos: number): string {
    return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  private iso(d: Date | null): string {
    if (!d) return '';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${dia}`;
  }
}
