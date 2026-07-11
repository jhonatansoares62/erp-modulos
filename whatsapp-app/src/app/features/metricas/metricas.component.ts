import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DatePickerModule } from 'primeng/datepicker';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { Custo, ResumoUso, WhatsAppApiService } from '../../core/whatsapp-api.service';

interface Linha {
  chave: string;
  valor: number;
}

/**
 * Painel de métricas do WhatsApp: consome GET /api/whatsapp/relatorios/resumo e
 * .../custo (Bearer automático). Cards de volume + breakdowns (status de entrega,
 * tipos de mensagem, desfecho do bot) e bloco de custo/volume vindo do Meta.
 */
@Component({
  selector: 'app-metricas',
  standalone: true,
  imports: [DecimalPipe, FormsModule, DatePickerModule, ButtonModule, CardModule, TableModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="filtros">
      <div class="field">
        <label>De</label>
        <p-datepicker [(ngModel)]="de" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" [maxDate]="hoje" />
      </div>
      <div class="field">
        <label>Até</label>
        <p-datepicker [(ngModel)]="ate" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" [maxDate]="hoje" />
      </div>
      <p-button label="Atualizar" icon="pi pi-refresh" [loading]="loading()" (onClick)="carregar()" />
      @if (periodoLabel(); as p) {
        <span class="periodo">{{ p }}</span>
      }
    </div>

    <div class="cards">
      <div class="metric-card">
        <span class="rotulo"><i class="pi pi-send"></i> Enviadas</span>
        <strong>{{ resumo()?.saida ?? 0 | number }}</strong>
      </div>
      <div class="metric-card">
        <span class="rotulo"><i class="pi pi-inbox"></i> Recebidas</span>
        <strong>{{ resumo()?.entrada ?? 0 | number }}</strong>
      </div>
      <div class="metric-card">
        <span class="rotulo"><i class="pi pi-dollar"></i> Faturáveis</span>
        <strong>{{ resumo()?.faturaveis ?? 0 | number }}</strong>
      </div>
      <div class="metric-card">
        <span class="rotulo"><i class="pi pi-comments"></i> Total</span>
        <strong>{{ resumo()?.total ?? 0 | number }}</strong>
      </div>
    </div>

    <div class="grid-breakdowns">
      <p-card>
        <ng-template #title><i class="pi pi-truck"></i> Status de entrega (saídas)</ng-template>
        @if (statusSaida().length) {
          <p-table [value]="statusSaida()" styleClass="p-datatable-sm">
            <ng-template #header>
              <tr><th>Status</th><th style="text-align:right">Qtde</th></tr>
            </ng-template>
            <ng-template #body let-l>
              <tr>
                <td><p-tag [value]="l.chave" [severity]="corStatus(l.chave)" /></td>
                <td style="text-align:right">{{ l.valor | number }}</td>
              </tr>
            </ng-template>
          </p-table>
        } @else {
          <p class="vazio">Sem saídas no período.</p>
        }
      </p-card>

      <p-card>
        <ng-template #title><i class="pi pi-tags"></i> Tipos de mensagem</ng-template>
        @if (porTipo().length) {
          <p-table [value]="porTipo()" styleClass="p-datatable-sm">
            <ng-template #header>
              <tr><th>Tipo</th><th style="text-align:right">Qtde</th></tr>
            </ng-template>
            <ng-template #body let-l>
              <tr><td><code>{{ l.chave }}</code></td><td style="text-align:right">{{ l.valor | number }}</td></tr>
            </ng-template>
          </p-table>
        } @else {
          <p class="vazio">Sem mensagens no período.</p>
        }
      </p-card>

      <p-card>
        <ng-template #title><i class="pi pi-android"></i> Resultado do bot (entradas)</ng-template>
        @if (porResultado().length) {
          <p-table [value]="porResultado()" styleClass="p-datatable-sm">
            <ng-template #header>
              <tr><th>Resultado</th><th style="text-align:right">Qtde</th></tr>
            </ng-template>
            <ng-template #body let-l>
              <tr>
                <td><p-tag [value]="l.chave" [severity]="corResultado(l.chave)" /></td>
                <td style="text-align:right">{{ l.valor | number }}</td>
              </tr>
            </ng-template>
          </p-table>
        } @else {
          <p class="vazio">Sem entradas no período.</p>
        }
      </p-card>
    </div>

    <p-card styleClass="custo-card">
      <ng-template #title><i class="pi pi-wallet"></i> Custo &amp; volume (Meta)</ng-template>
      @if (custo(); as c) {
        <div class="custo-topo">
          <div class="custo-item">
            <span class="rotulo">Custo total</span>
            <strong>{{ moeda(c.custoTotal) }}</strong>
          </div>
          <div class="custo-item">
            <span class="rotulo">Volume total</span>
            <strong>{{ c.volumeTotal | number }}</strong>
          </div>
          <div class="custo-item">
            <span class="rotulo">Faturável</span>
            <strong>{{ c.volumeFaturavel | number }}</strong>
          </div>
          <div class="custo-item">
            <span class="rotulo">Grátis</span>
            <strong>{{ c.volumeGratis | number }}</strong>
          </div>
        </div>

        <div class="custo-tabelas">
          <div>
            <h4>Por categoria</h4>
            @if (custoPorCategoria().length) {
              <p-table [value]="custoPorCategoria()" styleClass="p-datatable-sm">
                <ng-template #header>
                  <tr><th>Categoria</th><th style="text-align:right">Volume</th><th style="text-align:right">Custo</th></tr>
                </ng-template>
                <ng-template #body let-l>
                  <tr>
                    <td>{{ l.categoria }}</td>
                    <td style="text-align:right">{{ l.volume | number }}</td>
                    <td style="text-align:right">{{ moeda(l.custo) }}</td>
                  </tr>
                </ng-template>
              </p-table>
            } @else {
              <p class="vazio">Sem dados por categoria.</p>
            }
          </div>
          <div>
            <h4>Por tipo</h4>
            @if (volumePorTipo().length) {
              <p-table [value]="volumePorTipo()" styleClass="p-datatable-sm">
                <ng-template #header>
                  <tr><th>Tipo</th><th style="text-align:right">Volume</th></tr>
                </ng-template>
                <ng-template #body let-l>
                  <tr><td><code>{{ l.chave }}</code></td><td style="text-align:right">{{ l.valor | number }}</td></tr>
                </ng-template>
              </p-table>
            } @else {
              <p class="vazio">Sem dados por tipo.</p>
            }
          </div>
        </div>
      } @else {
        <p class="vazio">Custo indisponível para o período (ou Meta não retornou dados).</p>
      }
    </p-card>
  `,
  styles: [`
    :host { display: block; }
    .filtros { display: flex; align-items: flex-end; gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .periodo { font-size: .8rem; color: var(--text-color-secondary); align-self: center; }
    .cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; margin-bottom: 1.5rem; }
    @media (max-width: 720px) { .cards { grid-template-columns: repeat(2, 1fr); } }
    .metric-card { background: var(--surface-card); border: 1px solid var(--surface-border);
      border-radius: var(--content-border-radius); padding: 1.1rem 1.25rem; display: flex; flex-direction: column; gap: .4rem; }
    .metric-card .rotulo { font-size: .8rem; color: var(--text-color-secondary); display: inline-flex; align-items: center; gap: .4rem; }
    .metric-card .rotulo i { color: var(--primary-color); }
    .metric-card strong { font-family: var(--font-display); font-size: 1.9rem; line-height: 1; }
    .grid-breakdowns { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; margin-bottom: 1.5rem; }
    @media (max-width: 900px) { .grid-breakdowns { grid-template-columns: 1fr; } }
    .vazio { color: var(--text-color-secondary); font-size: .9rem; }
    .custo-topo { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; margin-bottom: 1.25rem; }
    @media (max-width: 720px) { .custo-topo { grid-template-columns: repeat(2, 1fr); } }
    .custo-item { display: flex; flex-direction: column; gap: .3rem; }
    .custo-item .rotulo { font-size: .78rem; color: var(--text-color-secondary); }
    .custo-item strong { font-size: 1.25rem; font-variant-numeric: tabular-nums; }
    .custo-tabelas { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
    @media (max-width: 720px) { .custo-tabelas { grid-template-columns: 1fr; } }
    .custo-tabelas h4 { margin: 0 0 .5rem; font-size: .9rem; }
  `],
})
export class MetricasComponent implements OnInit {
  private api = inject(WhatsAppApiService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  readonly hoje = new Date();

  loading = signal(false);
  resumo = signal<ResumoUso | null>(null);
  custo = signal<Custo | null>(null);

  de: Date = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
  ate: Date = new Date();

  readonly statusSaida = computed(() => this.mapToLinhas(this.resumo()?.statusSaida));
  readonly porTipo = computed(() => this.mapToLinhas(this.resumo()?.porTipo));
  readonly porResultado = computed(() => this.mapToLinhas(this.resumo()?.porResultado));
  readonly volumePorTipo = computed(() => this.mapToLinhas(this.custo()?.volumePorTipo));

  readonly custoPorCategoria = computed(() => {
    const c = this.custo();
    if (!c) return [];
    const volumes = c.volumePorCategoria ?? {};
    const custos = c.custoPorCategoria ?? {};
    const chaves = new Set([...Object.keys(volumes), ...Object.keys(custos)]);
    return [...chaves].sort().map((categoria) => ({
      categoria,
      volume: volumes[categoria] ?? 0,
      custo: custos[categoria] ?? 0,
    }));
  });

  readonly periodoLabel = computed(() => {
    const r = this.resumo();
    if (!r) return '';
    return `${this.fmtData(r.de)} — ${this.fmtData(r.ate)}`;
  });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    const de = this.iso(this.de);
    const ate = this.iso(this.ate);
    this.loading.set(true);
    let pendentes = 2;
    const done = () => { if (--pendentes === 0) this.loading.set(false); };

    this.api.resumo(de, ate).pipe(takeUntilDestroyed(this.destroyRef), finalize(done)).subscribe({
      next: (r) => this.resumo.set(r),
      error: () => this.msg.add({ severity: 'error', summary: 'Métricas', detail: 'Falha ao carregar o resumo de uso.' }),
    });

    this.api.custo(de, ate).pipe(takeUntilDestroyed(this.destroyRef), finalize(done)).subscribe({
      next: (c) => this.custo.set(c),
      error: () => {
        this.custo.set(null);
        this.msg.add({ severity: 'warn', summary: 'Métricas', detail: 'Custo indisponível (Meta não retornou dados).' });
      },
    });
  }

  moeda(valor: number | null | undefined): string {
    return (valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  corStatus(chave: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (chave) {
      case 'read': return 'success';
      case 'delivered': return 'info';
      case 'sent': return 'secondary';
      case 'failed': return 'danger';
      default: return 'warn';
    }
  }

  corResultado(chave: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (chave) {
      case 'respondido': return 'success';
      case 'nao_entendi': return 'warn';
      case 'sem_resposta': return 'secondary';
      case 'erro': return 'danger';
      default: return 'info';
    }
  }

  private mapToLinhas(mapa: Record<string, number> | null | undefined): Linha[] {
    if (!mapa) return [];
    return Object.entries(mapa)
      .map(([chave, valor]) => ({ chave, valor }))
      .sort((a, b) => b.valor - a.valor);
  }

  private fmtData(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? iso : d.toLocaleDateString('pt-BR');
  }

  private iso(d: Date | null): string {
    if (!d) return '';
    return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0).toISOString();
  }
}
