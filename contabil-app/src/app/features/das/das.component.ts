import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService } from 'primeng/api';
import { finalize } from 'rxjs';
import { PRIMENG_MODULES } from '../../shared/primeng';
import { BotaoComponent } from '../../shared/components/botao/botao.component';
import { ContabilidadeService, DasInfo } from '../../shared/services/contabilidade.service';

/**
 * DAS (Simples Nacional) nativo do módulo. Baixa o passivo 2.1.3.01 contra uma conta de
 * liquidação (Caixa/Bancos) escolhida pelo contador — sem forma de pagamento do ERP. O
 * registro por competência é único no módulo, então pagar aqui reflete no ERP e vice-versa,
 * sem dupla baixa.
 */
@Component({
  selector: 'app-das',
  standalone: true,
  imports: [FormsModule, DatePipe, ...PRIMENG_MODULES, BotaoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card">
      <div class="das-top">
        <div class="saldo-box">
          <span class="saldo-label">Saldo a recolher (2.1.3.01 Simples Nacional)</span>
          <span class="saldo-val">{{ reais(info()?.saldoCentavos ?? 0) }}</span>
        </div>
        <app-botao label="Atualizar" icon="pi pi-refresh" [text]="true" size="small" (clicado)="carregar()" />
      </div>

      <div class="das-form">
        <div class="field">
          <label>Competência</label>
          <p-datepicker [(ngModel)]="competencia" view="month" dateFormat="yy-mm" [showIcon]="true"
                        appendTo="body" (onSelect)="competenciaMudou()" placeholder="AAAA-MM" />
        </div>
        <div class="field">
          <label>Conta de liquidação</label>
          <p-select [(ngModel)]="contaLiquidacao" [options]="contasLiquidacao" optionLabel="label" optionValue="value"
                    appendTo="body" styleClass="w-full" />
        </div>
        <div class="field">
          <label>Valor do DAS</label>
          <p-inputnumber [(ngModel)]="valorReais" mode="currency" currency="BRL" locale="pt-BR"
                         [min]="0" placeholder="R$ 0,00" />
        </div>
        <div class="field">
          <label>Data do pagamento</label>
          <p-datepicker [(ngModel)]="dataPagamento" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
        </div>
        <app-botao label="Pagar DAS" icon="pi pi-check" [loading]="pagando()"
                   [disabled]="!podePagar()" (clicado)="confirmarPagar()" />
      </div>

      @if (sugestao(); as s) {
        <div class="das-sugestao">
          <i class="pi pi-info-circle"></i>
          @if (info()?.competenciaPaga) {
            <span>A competência <strong>{{ compIso() }}</strong> já está paga.</span>
          } @else {
            <span>Imposto apurado em <strong>{{ compIso() }}</strong>:
              <strong>{{ reais(s) }}</strong></span>
            <app-botao label="Usar valor sugerido" icon="pi pi-arrow-down" size="small" severity="secondary"
                       (clicado)="usarSugestao(s)" />
          }
        </div>
      }
    </div>

    <div class="card">
      <h3>Pagamentos registrados</h3>
      @if (info()?.historico?.length) {
        <p-table [value]="info()!.historico" styleClass="p-datatable-sm">
          <ng-template #header>
            <tr>
              <th style="width:110px">Competência</th>
              <th style="text-align:right">Valor</th>
              <th>Conta de liquidação</th>
              <th style="width:110px">Data</th>
              <th style="width:90px">Origem</th>
            </tr>
          </ng-template>
          <ng-template #body let-p>
            <tr>
              <td><code>{{ p.competencia }}</code></td>
              <td style="text-align:right">{{ reais(p.valorCentavos) }}</td>
              <td><code>{{ p.contaLiquidacao }}</code> {{ p.contaLiquidacaoNome }}</td>
              <td>{{ p.dataPagamento | date:'dd/MM/yyyy' }}</td>
              <td>
                <p-tag [value]="p.origem === 'erp' ? 'ERP' : 'App'"
                       [severity]="p.origem === 'erp' ? 'warn' : 'info'" />
              </td>
            </tr>
          </ng-template>
        </p-table>
      } @else {
        <p class="vazio">Nenhum DAS pago ainda.</p>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .card { background: var(--surface-card); border: 1px solid var(--surface-border);
      border-radius: var(--content-border-radius); padding: 1.25rem 1.5rem; margin-bottom: 1.25rem; }
    .card h3 { margin: 0 0 1rem; font-size: 1rem; }
    .das-top { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: 1.25rem; }
    .saldo-box { display: flex; flex-direction: column; gap: .15rem; }
    .saldo-label { font-size: .78rem; color: var(--text-color-secondary); text-transform: uppercase; letter-spacing: .04em; }
    .saldo-val { font-family: var(--font-display, inherit); font-size: 1.9rem; font-weight: 800; color: var(--color-danger); }
    .das-form { display: flex; align-items: flex-end; gap: 1rem; flex-wrap: wrap; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    :host ::ng-deep .w-full { width: 100%; }
    .das-sugestao { display: flex; align-items: center; gap: .6rem; margin-top: 1rem; padding: .6rem .9rem;
      border: 1px solid var(--surface-border); border-radius: 8px; background: var(--surface-ground);
      font-size: .88rem; flex-wrap: wrap; }
    .das-sugestao i { color: var(--color-info); }
    .vazio { color: var(--text-color-secondary); }
  `],
})
export class DasComponent implements OnInit {
  private service = inject(ContabilidadeService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);
  private confirm = inject(ConfirmationService);

  info = signal<DasInfo | null>(null);
  sugestao = signal<number | null>(null);
  pagando = signal(false);

  competencia: Date = new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1);
  contaLiquidacao = '1.1.1.02';
  valorReais: number | null = null;
  dataPagamento: Date = new Date();

  readonly contasLiquidacao = [
    { label: 'Bancos / PIX (1.1.1.02)', value: '1.1.1.02' },
    { label: 'Caixa (1.1.1.01)', value: '1.1.1.01' },
  ];

  ngOnInit(): void {
    this.carregar();
  }

  compIso(): string {
    const d = this.competencia;
    return d ? `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}` : '';
  }

  carregar(): void {
    this.service.dasInfo(this.compIso()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (i) => { this.info.set(i); this.sugestao.set(i.saldoCompetenciaCentavos); },
      error: () => this.msg.add({ severity: 'error', summary: 'DAS', detail: 'Falha ao carregar o DAS.' }),
    });
  }

  competenciaMudou(): void {
    this.carregar();
  }

  usarSugestao(centavos: number): void {
    this.valorReais = centavos / 100;
  }

  podePagar(): boolean {
    return !!this.competencia && !!this.contaLiquidacao && (this.valorReais ?? 0) > 0
      && !this.info()?.competenciaPaga;
  }

  confirmarPagar(): void {
    if (!this.podePagar()) return;
    const contaNome = this.contasLiquidacao.find((c) => c.value === this.contaLiquidacao)?.label ?? this.contaLiquidacao;
    this.confirm.confirm({
      header: 'Confirmar pagamento do DAS',
      message: `Pagar o DAS da competência ${this.compIso()} no valor de ${this.reais(Math.round((this.valorReais ?? 0) * 100))}, `
        + `creditando ${contaNome}? Isso baixa o Simples Nacional a Recolher (2.1.3.01).`,
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.pagar(),
    });
  }

  private pagar(): void {
    this.pagando.set(true);
    this.service.pagarDas({
      competencia: this.compIso(),
      valorCentavos: Math.round((this.valorReais ?? 0) * 100),
      contaLiquidacao: this.contaLiquidacao,
      dataPagamento: this.isoData(this.dataPagamento),
    }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.pagando.set(false))).subscribe({
      next: (p) => {
        this.msg.add({ severity: 'success', summary: 'DAS pago',
          detail: `DAS ${p.competencia} de ${this.reais(p.valorCentavos)} contabilizado.` });
        this.valorReais = null;
        this.carregar();
      },
      error: (err) => this.msg.add({ severity: 'error', summary: 'DAS',
        detail: err?.error?.detalhe || err?.error?.mensagem || err?.error?.message
          || (err?.status === 409 ? 'Competência já paga.' : 'Falha ao pagar o DAS.') }),
    });
  }

  reais(centavos: number): string {
    return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  private isoData(d: Date | null): string | undefined {
    if (!d) return undefined;
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
