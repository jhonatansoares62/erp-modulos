import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { finalize } from 'rxjs';
import { PRIMENG_MODULES } from '../../shared/primeng';
import { BotaoComponent } from '../../shared/components/botao/botao.component';
import { ApuracaoFiscal, ContabilidadeService, FiscalConfig } from '../../shared/services/contabilidade.service';

/**
 * Aba Fiscal (Simples Nacional): configura regime/anexo/início/folha e mostra a apuração
 * automática — RBT12 (dos 12 meses do razão), Fator R, anexo efetivo, faixa e alíquota efetiva.
 * Com cálculo automático ligado, essa alíquota alimenta o imposto (substitui a manual do Item 2).
 */
@Component({
  selector: 'app-config-fiscal-tab',
  standalone: true,
  imports: [FormsModule, ...PRIMENG_MODULES, BotaoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="hint">Cálculo automático da alíquota do Simples por RBT12 + Fator R + faixas do anexo.
      Ligado, alimenta o imposto apurado; desligado, usa a alíquota manual (Empresa → Tributação).</p>

    <div class="fis-cols">
      <div class="fis-card">
        <h4>Configuração</h4>
        <div class="fis-form">
          <div class="linha">
            <label>Cálculo automático</label>
            <p-toggleswitch [(ngModel)]="cfg.calculoAutomatico" />
          </div>
          <div class="field">
            <label>Anexo</label>
            <p-select [(ngModel)]="cfg.anexo" [options]="anexos" optionLabel="label" optionValue="value"
                      appendTo="body" styleClass="w-full" />
          </div>
          <div class="field">
            <label>Data de início de atividade</label>
            <p-datepicker [(ngModel)]="inicio" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
          </div>
          <div class="field">
            <label>Folha 12m (Salários / pró-labore)</label>
            <p-inputnumber [(ngModel)]="folhaReais" mode="currency" currency="BRL" locale="pt-BR"
                           [min]="0" placeholder="R$ 0,00" styleClass="w-full" />
          </div>
          <app-botao label="Salvar" icon="pi pi-check" [loading]="salvando()" (clicado)="salvar()" />
        </div>
      </div>

      <div class="fis-card">
        <h4>Apuração {{ apuracao() ? '' : '' }}</h4>
        @if (apuracao(); as a) {
          <div class="fis-ap">
            <div class="linha"><span>RBT12 {{ a.proporcionalizado ? '(proporcionalizado ' + a.mesesAtividade + 'm)' : '' }}</span><strong>{{ reais(a.rbt12Centavos) }}</strong></div>
            <div class="linha"><span>Folha 12m</span><span>{{ reais(a.folha12mCentavos) }}</span></div>
            <div class="linha"><span>Fator R</span><span>{{ (a.fatorR * 100).toFixed(2) }}%</span></div>
            <div class="linha"><span>Anexo efetivo</span><strong>{{ a.anexoEfetivo }}<small> ({{ a.anexoConfigurado === 'AUTO' ? 'auto por Fator R' : 'fixo' }})</small></strong></div>
            <div class="linha"><span>Faixa</span><span>{{ a.faixa ?? '—' }}</span></div>
            <div class="linha"><span>Alíquota nominal</span><span>{{ a.aliquotaNominal }}%</span></div>
            <div class="linha"><span>Parcela a deduzir</span><span>{{ reais(a.parcelaDeduzirCentavos) }}</span></div>
            <div class="linha total"><span>Alíquota efetiva</span><strong>{{ a.aliquotaEfetiva }}%</strong></div>
            @if (a.excedeSimples) {
              <p class="alerta">RBT12 excede o teto do Simples (R$ 4.800.000,00).</p>
            }
            <p class="obs">Imposto apurado hoje: <strong>{{ a.automatico ? 'alíquota efetiva calculada (' + a.aliquotaEfetiva + '%)' : 'alíquota manual do Item 2' }}</strong>.</p>
          </div>
        } @else {
          <p class="vazio">Sem apuração disponível.</p>
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .hint { margin: 0 0 1.25rem; font-size: .85rem; color: var(--text-color-secondary); max-width: 52rem; }
    .fis-cols { display: flex; gap: 1.5rem; flex-wrap: wrap; align-items: flex-start; }
    .fis-card { flex: 1 1 22rem; border: 1px solid var(--surface-border); border-radius: 10px; padding: 1.1rem 1.25rem; background: var(--surface-0); }
    .fis-card h4 { margin: 0 0 1rem; font-size: 1rem; }
    .fis-form { display: flex; flex-direction: column; gap: 1rem; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .fis-form .linha { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
    .fis-form .linha label { font-size: .8rem; color: var(--text-color-secondary); }
    .w-full { width: 100%; }
    .fis-ap .linha { display: flex; justify-content: space-between; gap: 1rem; padding: .45rem 0; border-bottom: 1px solid var(--surface-border); }
    .fis-ap .total { border-bottom: none; border-top: 2px solid var(--surface-border); margin-top: .25rem; padding-top: .6rem; }
    .fis-ap .total strong { font-size: 1.15rem; color: var(--primary-color); }
    .fis-ap small { color: var(--text-color-secondary); font-weight: 400; }
    .obs { margin: .75rem 0 0; font-size: .78rem; color: var(--text-color-secondary); }
    .alerta { color: var(--red-600, #dc2626); font-size: .8rem; margin: .5rem 0 0; }
    .vazio { color: var(--text-color-secondary); }
  `],
})
export class ConfigFiscalTabComponent implements OnInit {
  private service = inject(ContabilidadeService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  salvando = signal(false);
  apuracao = signal<ApuracaoFiscal | null>(null);

  anexos = [
    { label: 'Automático por Fator R (III/V)', value: 'AUTO' },
    { label: 'Anexo III (serviços)', value: 'III' },
    { label: 'Anexo V (serviços)', value: 'V' },
    { label: 'Anexo I (comércio)', value: 'I' },
    { label: 'Anexo II (indústria)', value: 'II' },
  ];

  cfg: FiscalConfig = { regime: 'simples_nacional', calculoAutomatico: false, anexo: 'AUTO', dataInicioAtividade: null, folha12mCentavos: 0 };
  inicio: Date | null = null;
  folhaReais = 0;

  ngOnInit(): void {
    this.service.fiscalConfig().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (c) => {
        this.cfg = c;
        this.folhaReais = (c.folha12mCentavos ?? 0) / 100;
        if (c.dataInicioAtividade) {
          const [y, m, d] = c.dataInicioAtividade.split('-').map(Number);
          this.inicio = new Date(y, m - 1, d);
        }
      },
      error: () => {},
    });
    this.carregarApuracao();
  }

  salvar(): void {
    const dto: FiscalConfig = {
      regime: this.cfg.regime || 'simples_nacional',
      calculoAutomatico: !!this.cfg.calculoAutomatico,
      anexo: this.cfg.anexo || 'AUTO',
      dataInicioAtividade: this.inicio ? this.iso(this.inicio) : null,
      folha12mCentavos: Math.round((this.folhaReais ?? 0) * 100),
    };
    this.salvando.set(true);
    this.service.salvarFiscalConfig(dto).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.salvando.set(false)),
    ).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Fiscal', detail: 'Configuração fiscal salva.' });
        this.carregarApuracao();
      },
      error: (err) => this.msg.add({ severity: 'error', summary: 'Fiscal',
        detail: err?.error?.mensagem || err?.error?.message || err?.error?.error || 'Falha ao salvar a configuração fiscal.' }),
    });
  }

  private carregarApuracao(): void {
    this.service.fiscalApuracao().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (a) => this.apuracao.set(a),
      error: () => this.apuracao.set(null),
    });
  }

  reais(c: number): string {
    return (c / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  private iso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
