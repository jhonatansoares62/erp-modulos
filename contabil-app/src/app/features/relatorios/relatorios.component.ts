import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { Balanco, Balancete, ContabilidadeService, Dre } from '../../shared/services/contabilidade.service';

/**
 * Relatórios contábeis read-only: Balancete (com indicador débitos=créditos), DRE e Balanço
 * Patrimonial. Espelha a aba embutida do Odonto, batendo direto na api-contabil (8750).
 */
@Component({
  selector: 'app-relatorios',
  standalone: true,
  imports: [FormsModule, DatePickerModule, TableModule, TagModule, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rel-filtros">
      <div class="field">
        <label>De</label>
        <p-datepicker [(ngModel)]="de" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
      </div>
      <div class="field">
        <label>Até</label>
        <p-datepicker [(ngModel)]="ate" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
      </div>
      <p-button label="Gerar" icon="pi pi-search" [loading]="loading()" (onClick)="gerar()" />
    </div>

    <div class="rel-secao">
      <h3>Balancete de verificação</h3>
      @if (balancete(); as b) {
        <p-table [value]="b.linhas" styleClass="p-datatable-sm">
          <ng-template #header>
            <tr>
              <th>Código</th>
              <th>Conta</th>
              <th style="text-align:right">Saldo anterior</th>
              <th style="text-align:right">Débitos</th>
              <th style="text-align:right">Créditos</th>
              <th style="text-align:right">Saldo atual</th>
            </tr>
          </ng-template>
          <ng-template #body let-l>
            <tr>
              <td><code>{{ l.codigo }}</code></td>
              <td>{{ l.nome }}</td>
              <td style="text-align:right">
                @if (l.saldoAnteriorCentavos) {
                  {{ reais(l.saldoAnteriorCentavos) }} <small class="nat">{{ l.saldoAnteriorNatureza }}</small>
                } @else {
                  <span class="zero">—</span>
                }
              </td>
              <td style="text-align:right">{{ reais(l.debitos) }}</td>
              <td style="text-align:right">{{ reais(l.creditos) }}</td>
              <td style="text-align:right">{{ reais(l.saldoAtualCentavos) }} <small class="nat">{{ l.saldoAtualNatureza }}</small></td>
            </tr>
          </ng-template>
          <ng-template #footer>
            <tr class="rel-total">
              <td colspan="3">
                @if (b.fecha) {
                  <p-tag value="Movimento confere" severity="success" />
                } @else {
                  <p-tag value="Movimento não confere" severity="danger" />
                }
              </td>
              <td style="text-align:right">{{ reais(b.totalDebitos) }}</td>
              <td style="text-align:right">{{ reais(b.totalCreditos) }}</td>
              <td></td>
            </tr>
            <tr class="rel-total">
              <td colspan="6" style="text-align:right">
                @if (b.fechaSaldos) {
                  <p-tag value="Saldos conferem" severity="success" />
                } @else {
                  <p-tag value="Saldos não conferem" severity="danger" />
                }
                <small class="tot-saldos">Σ devedores {{ reais(b.totalSaldoAtualDevedorCentavos) }} · Σ credores {{ reais(b.totalSaldoAtualCredorCentavos) }}</small>
              </td>
            </tr>
          </ng-template>
        </p-table>
      } @else {
        <p class="vazio">Informe o período e clique em Gerar.</p>
      }
    </div>

    <div class="rel-secao">
      <h3>DRE — Demonstração do Resultado</h3>
      @if (dre(); as d) {
        <div class="dre">
          <div class="dre-linha"><span>Receita Bruta</span><span>{{ reais(d.receitaBruta) }}</span></div>
          <div class="dre-linha"><span>(-) Deduções de Tributos</span><span>{{ reais(d.deducoes) }}</span></div>
          <div class="dre-linha"><span>(-) Devoluções/Cancelamentos</span><span>{{ reais(d.devolucoes) }}</span></div>
          <div class="dre-linha dre-linha--sub"><span>= Receita Líquida</span><span>{{ reais(d.receitaLiquida) }}</span></div>
          <div class="dre-linha"><span>(-) Custos</span><span>{{ reais(d.custos) }}</span></div>
          <div class="dre-linha dre-linha--sub"><span>= Lucro Bruto</span><span>{{ reais(d.lucroBruto) }}</span></div>
          <div class="dre-linha"><span>(-) Despesas Operacionais</span><span>{{ reais(d.despesasOperacionais) }}</span></div>
          <div class="dre-linha"><span>(-) Despesas Financeiras</span><span>{{ reais(d.despesasFinanceiras) }}</span></div>
          <div class="dre-linha"><span>(+) Receitas Financeiras</span><span>{{ reais(d.receitasFinanceiras) }}</span></div>
          <div class="dre-linha dre-linha--sub"><span>= Resultado Líquido</span><span>{{ reais(d.resultadoLiquido) }}</span></div>
        </div>
      } @else {
        <p class="vazio">Informe o período e clique em Gerar.</p>
      }
    </div>

    <div class="rel-secao">
      <h3>Balanço Patrimonial <small>(posição em {{ dataPosicao() }})</small></h3>
      @if (balanco(); as bp) {
        <div class="bp">
          <div class="bp-col">
            <h4>Ativo</h4>
            @for (l of bp.ativo; track l.codigo) {
              <div class="bp-linha"><span><code>{{ l.codigo }}</code> {{ l.nome }}</span><span>{{ reais(l.saldoCentavos) }}</span></div>
            }
            <div class="bp-linha bp-total"><span>Total do Ativo</span><span>{{ reais(bp.totalAtivo) }}</span></div>
          </div>
          <div class="bp-col">
            <h4>Passivo + Patrimônio Líquido</h4>
            @for (l of bp.passivoPl; track l.codigo) {
              <div class="bp-linha"><span><code>{{ l.codigo }}</code> {{ l.nome }}</span><span>{{ reais(l.saldoCentavos) }}</span></div>
            }
            <div class="bp-linha bp-total"><span>Total do Passivo + PL</span><span>{{ reais(bp.totalPassivoPl) }}</span></div>
          </div>
        </div>
        <div class="bp-fecha">
          @if (bp.fecha) {
            <p-tag value="Confere" severity="success" />
          } @else {
            <p-tag value="Não confere" severity="danger" />
          }
        </div>
      } @else {
        <p class="vazio">Informe o período e clique em Gerar.</p>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .rel-filtros { display: flex; align-items: flex-end; gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .rel-secao { margin-bottom: 2rem; background: var(--surface-card); border: 1px solid var(--surface-border);
      border-radius: var(--content-border-radius); padding: 1.25rem 1.5rem; }
    .rel-secao h3 { margin: 0 0 .75rem; font-size: 1rem; }
    .rel-secao h3 small { font-weight: 400; font-size: .8rem; color: var(--text-color-secondary); }
    .vazio { color: var(--text-color-secondary); }
    .rel-total td { font-weight: 600; }
    .nat { color: var(--text-color-secondary); font-size: 0.72rem; margin-left: .15rem; }
    .zero { color: var(--text-color-secondary); }
    .tot-saldos { margin-left: .6rem; font-weight: 400; color: var(--text-color-secondary); font-size: .8rem; }
    .dre { max-width: 32rem; }
    .dre-linha { display: flex; justify-content: space-between; padding: .4rem 0; border-bottom: 1px solid var(--surface-border); }
    .dre-linha span:last-child { font-variant-numeric: tabular-nums; }
    .dre-linha--sub { font-weight: 700; }
    .bp { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
    @media (max-width: 640px) { .bp { grid-template-columns: 1fr; } }
    .bp-col h4 { margin: 0 0 .5rem; font-size: .9rem; }
    .bp-linha { display: flex; justify-content: space-between; gap: 1rem; padding: .35rem 0; border-bottom: 1px solid var(--surface-border); font-size: .9rem; }
    .bp-linha span:last-child { font-variant-numeric: tabular-nums; white-space: nowrap; }
    .bp-total { font-weight: 700; border-bottom: none; border-top: 2px solid var(--surface-border); margin-top: .25rem; }
    .bp-fecha { margin-top: 1rem; }
  `],
})
export class RelatoriosComponent implements OnInit {
  private service = inject(ContabilidadeService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  loading = signal(false);
  balancete = signal<Balancete | null>(null);
  dre = signal<Dre | null>(null);
  balanco = signal<Balanco | null>(null);

  de: Date = new Date(new Date().getFullYear(), 0, 1);
  ate: Date = new Date();

  dataPosicao(): string {
    return this.ate ? this.ate.toLocaleDateString('pt-BR') : '—';
  }

  ngOnInit(): void {
    this.gerar();
  }

  gerar(): void {
    const de = this.iso(this.de);
    const ate = this.iso(this.ate);
    if (!de || !ate) {
      this.msg.add({ severity: 'warn', summary: 'Contabilidade', detail: 'Informe as datas de e até.' });
      return;
    }
    this.loading.set(true);
    let pendentes = 3;
    const done = () => { if (--pendentes === 0) this.loading.set(false); };

    this.service.balancete(de, ate).pipe(takeUntilDestroyed(this.destroyRef), finalize(done)).subscribe({
      next: (b) => this.balancete.set(b),
      error: () => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: 'Falha ao gerar o balancete.' }),
    });

    this.service.dre(de, ate).pipe(takeUntilDestroyed(this.destroyRef), finalize(done)).subscribe({
      next: (d) => this.dre.set(d),
      error: () => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: 'Falha ao gerar a DRE.' }),
    });

    this.service.balanco(ate).pipe(takeUntilDestroyed(this.destroyRef), finalize(done)).subscribe({
      next: (bp) => this.balanco.set(bp),
      error: () => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: 'Falha ao gerar o balanço patrimonial.' }),
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
