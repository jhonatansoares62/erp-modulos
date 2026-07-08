import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService } from 'primeng/api';
import { finalize } from 'rxjs';
import { PRIMENG_MODULES } from '../../shared/primeng';
import { BotaoComponent } from '../../shared/components/botao/botao.component';
import { ContabilidadeService } from '../../shared/services/contabilidade.service';

/**
 * Saldos de abertura reais (onboarding): o admin informa, numa data de abertura, os saldos
 * iniciais (Caixa, Bancos, Capital). Postado como D ativos / C Capital, com residual em
 * Lucros/Prejuízos Acumulados. Havendo informado, o sistema deixa de inferir capital para
 * zerar liquidez negativa (auto-aporte vira fallback).
 */
@Component({
  selector: 'app-config-abertura-tab',
  standalone: true,
  imports: [FormsModule, ...PRIMENG_MODULES, BotaoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="hint">Informe os saldos iniciais REAIS da clínica na data de abertura. Enquanto não
      informados, o sistema infere um Capital Social para zerar contas de liquidez negativas.</p>

    @if (informada()) {
      <div class="ab-estado"><i class="pi pi-check-circle"></i> Saldos de abertura informados em
        <strong>{{ dataBr(dataAbertura) }}</strong>. Você pode reinformar para atualizar.</div>
    }

    <div class="ab-form">
      <div class="field">
        <label>Data de abertura</label>
        <p-datepicker [(ngModel)]="data" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
      </div>
      <div class="field">
        <label>Caixa (1.1.1.01)</label>
        <p-inputnumber [(ngModel)]="caixaReais" mode="currency" currency="BRL" locale="pt-BR"
                       [min]="0" placeholder="R$ 0,00" styleClass="w-full" />
      </div>
      <div class="field">
        <label>Bancos (1.1.1.02)</label>
        <p-inputnumber [(ngModel)]="bancosReais" mode="currency" currency="BRL" locale="pt-BR"
                       [min]="0" placeholder="R$ 0,00" styleClass="w-full" />
      </div>
      <div class="field">
        <label>Capital Social (2.3.1.01)</label>
        <p-inputnumber [(ngModel)]="capitalReais" mode="currency" currency="BRL" locale="pt-BR"
                       [min]="0" placeholder="R$ 0,00" styleClass="w-full" />
        <button type="button" class="link-sugestao" (click)="usarSomaComoCapital()">
          = soma dos ativos ({{ reais(somaAtivosCentavos()) }})</button>
      </div>
      <app-botao label="Salvar abertura" icon="pi pi-check" [loading]="salvando()" (clicado)="confirmar()" />
    </div>

    @if (residualCentavos() !== 0) {
      <p class="ab-residual">
        @if (residualCentavos() > 0) {
          Diferença de {{ reais(residualCentavos()) }} (ativos &gt; capital) entra em <strong>Lucros Acumulados</strong>.
        } @else {
          Diferença de {{ reais(-residualCentavos()) }} (capital &gt; ativos) entra em <strong>(-) Prejuízos Acumulados</strong>.
        }
      </p>
    }
  `,
  styles: [`
    :host { display: block; }
    .hint { margin: 0 0 1rem; font-size: .85rem; color: var(--text-color-secondary); max-width: 46rem; }
    .ab-estado { display: flex; align-items: center; gap: .5rem; margin-bottom: 1.25rem; padding: .6rem .9rem;
      border: 1px solid var(--green-300, #86efac); border-radius: 8px; background: var(--green-50, #f0fdf4);
      font-size: .85rem; }
    .ab-estado i { color: var(--green-600, #16a34a); }
    .ab-form { display: flex; align-items: flex-end; gap: 1rem; margin-bottom: 1.75rem; flex-wrap: wrap; }
    .field { display: flex; flex-direction: column; gap: .35rem; min-width: 12rem; position: relative; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .w-full { width: 100%; }
    /* Fora do fluxo pra não desalinhar o input dos demais campos (align-items: flex-end). */
    .link-sugestao { position: absolute; left: 0; bottom: -1.15rem; white-space: nowrap;
      background: none; border: none; padding: 0; margin: 0; cursor: pointer;
      font-size: .72rem; color: var(--primary-color); text-align: left; }
    .ab-residual { font-size: .82rem; color: var(--text-color-secondary); }
  `],
})
export class ConfigAberturaTabComponent implements OnInit {
  private service = inject(ContabilidadeService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);
  private confirm = inject(ConfirmationService);

  informada = signal(false);
  dataAbertura: string | null = null;
  salvando = signal(false);

  data: Date = new Date(new Date().getFullYear(), 0, 1);
  caixaReais = 0;
  bancosReais = 0;
  capitalReais = 0;

  ngOnInit(): void {
    this.service.abertura().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (a) => {
        this.informada.set(a.informada);
        this.dataAbertura = a.dataAbertura;
        if (a.informada && a.dataAbertura) {
          const [y, m, d] = a.dataAbertura.split('-').map(Number);
          this.data = new Date(y, m - 1, d);
        }
        for (const s of a.saldos ?? []) {
          const v = s.valorCentavos / 100;
          if (s.codigo === '1.1.1.01') this.caixaReais = v;
          else if (s.codigo === '1.1.1.02') this.bancosReais = v;
          else if (s.codigo === '2.3.1.01') this.capitalReais = v;
        }
      },
      error: () => {},
    });
  }

  somaAtivosCentavos(): number {
    return Math.round((this.caixaReais ?? 0) * 100) + Math.round((this.bancosReais ?? 0) * 100);
  }

  residualCentavos(): number {
    return this.somaAtivosCentavos() - Math.round((this.capitalReais ?? 0) * 100);
  }

  usarSomaComoCapital(): void {
    this.capitalReais = this.somaAtivosCentavos() / 100;
  }

  confirmar(): void {
    const caixa = Math.round((this.caixaReais ?? 0) * 100);
    const bancos = Math.round((this.bancosReais ?? 0) * 100);
    const capital = Math.round((this.capitalReais ?? 0) * 100);
    if (caixa + bancos + capital <= 0) {
      this.msg.add({ severity: 'warn', summary: 'Abertura', detail: 'Informe ao menos um saldo maior que zero.' });
      return;
    }
    if (!this.data) {
      this.msg.add({ severity: 'warn', summary: 'Abertura', detail: 'Informe a data de abertura.' });
      return;
    }
    this.confirm.confirm({
      header: 'Confirmar saldos de abertura',
      message: `Registrar a abertura em ${this.data.toLocaleDateString('pt-BR')}? Isso substitui o aporte`
        + ` automático de capital pelos valores informados.`,
      icon: 'pi pi-check-circle',
      accept: () => this.salvar(caixa, bancos, capital),
    });
  }

  private salvar(caixa: number, bancos: number, capital: number): void {
    const saldos = [
      { codigo: '1.1.1.01', valorCentavos: caixa },
      { codigo: '1.1.1.02', valorCentavos: bancos },
      { codigo: '2.3.1.01', valorCentavos: capital },
    ].filter((s) => s.valorCentavos > 0);

    this.salvando.set(true);
    this.service.informarAbertura({ dataAbertura: this.iso(this.data), saldos }).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.salvando.set(false)),
    ).subscribe({
      next: (a) => {
        this.informada.set(a.informada);
        this.dataAbertura = a.dataAbertura;
        this.msg.add({ severity: 'success', summary: 'Abertura', detail: 'Saldos de abertura registrados.' });
      },
      error: (err) => this.msg.add({ severity: 'error', summary: 'Abertura',
        detail: err?.error?.mensagem || err?.error?.message || err?.error?.error || 'Falha ao salvar os saldos de abertura.' }),
    });
  }

  reais(c: number): string {
    return (c / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  dataBr(iso: string | null): string {
    if (!iso) return '—';
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('pt-BR');
  }

  private iso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
