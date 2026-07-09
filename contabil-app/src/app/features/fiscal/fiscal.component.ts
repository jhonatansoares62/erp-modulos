import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { finalize } from 'rxjs';
import { PRIMENG_MODULES } from '../../shared/primeng';
import { BotaoComponent } from '../../shared/components/botao/botao.component';
import {
  ApuracaoFiscal, ContabilidadeService, FiscalConfig, MemoriaFiscal, ReceitaHistoricaItem,
} from '../../shared/services/contabilidade.service';

/**
 * Aba Fiscal (Simples Nacional): configura regime/anexo/início/corte/folha, mostra a apuração
 * automática (RBT12 dos 12 meses ANTERIORES à competência, Fator R, anexo, faixa, alíquota efetiva),
 * a memória de cálculo por competência (com view imprimível) e, para empresa migrando, a receita
 * bruta histórica dos 12 meses anteriores ao corte (parâmetro fiscal, não é lançamento).
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
          <div class="dois">
            <div class="field">
              <label>Data de início de atividade</label>
              <p-datepicker [(ngModel)]="inicio" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
            </div>
            <div class="field">
              <label>Data de entrada no sistema (corte)</label>
              <p-datepicker [(ngModel)]="corte" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body"
                            (onSelect)="regenerarGrade()" (onClear)="regenerarGrade()" />
            </div>
          </div>
          <div class="field">
            <label>Folha 12m (Salários / pró-labore)</label>
            <p-inputnumber [(ngModel)]="folhaReais" mode="currency" currency="BRL" locale="pt-BR"
                           [min]="0" placeholder="R$ 0,00" styleClass="w-full" />
          </div>
          <app-botao label="Salvar" icon="pi pi-check" [loading]="salvando()" (clicado)="salvar()" />
        </div>

        @if (corte && historico.length) {
          <div class="hist">
            <h5>Receita bruta histórica (12 meses anteriores ao corte)</h5>
            <p class="hint2">Empresa migrando (&gt; 1 ano): informe a receita bruta dos 12 meses antes do corte
              para o RBT12 cair na faixa/alíquota corretas antes de qualquer venda escriturada.</p>
            <div class="hist-grid">
              @for (h of historico; track h.competencia) {
                <div class="hist-linha">
                  <label>{{ competenciaLabel(h.competencia) }}</label>
                  <p-inputnumber [(ngModel)]="h.reais" mode="currency" currency="BRL" locale="pt-BR"
                                 [min]="0" placeholder="R$ 0,00" styleClass="w-full" />
                </div>
              }
            </div>
            <div class="hist-total"><span>Total</span><strong>{{ reais(totalHistoricoCentavos()) }}</strong></div>
            <app-botao label="Salvar histórico" icon="pi pi-save" [loading]="salvandoHist()" (clicado)="salvarHistorico()" />
          </div>
        }
      </div>

      <div class="fis-card">
        <h4>Apuração</h4>
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

    <div class="fis-card mem">
      <div class="mem-head">
        <h4>Memória de cálculo</h4>
        <div class="mem-controls">
          <p-datepicker [(ngModel)]="competenciaMes" view="month" dateFormat="mm/yy" [showIcon]="true"
                        appendTo="body" (onSelect)="carregarMemoria()" />
          <p-button icon="pi pi-print" label="Imprimir / PDF" [outlined]="true" size="small" (onClick)="imprimir()" />
          <p-button [icon]="memoriaAberta() ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" [text]="true"
                    size="small" (onClick)="memoriaAberta.set(!memoriaAberta())" />
        </div>
      </div>

      @if (memoriaAberta()) {
        @if (memoria(); as m) {
          <div class="mem-body">
            <div class="passos">
              @for (p of m.passos; track p.ordem) {
                <div class="passo">
                  <div class="passo-t"><span class="ord">{{ p.ordem }}</span>{{ p.titulo }}</div>
                  <div class="passo-f">{{ p.formula }}</div>
                  <div class="passo-v">{{ p.valor }}</div>
                </div>
              }
            </div>

            <h5>Janela do RBT12 — {{ m.mesesAtividade }} {{ m.mesesAtividade === 1 ? 'mês' : 'meses' }}{{ m.proporcionalizado ? ' (proporcionalizado)' : '' }}</h5>
            <p-table [value]="m.janela" styleClass="p-datatable-sm">
              <ng-template #header>
                <tr><th>Competência</th><th style="text-align:right">Receita</th><th>Fonte</th></tr>
              </ng-template>
              <ng-template #body let-i>
                <tr>
                  <td>{{ competenciaLabel(i.competencia) }}</td>
                  <td style="text-align:right">{{ reais(i.receitaCentavos) }}</td>
                  <td><p-tag [value]="i.fonte" [severity]="i.fonte === 'informado' ? 'warn' : 'success'" /></td>
                </tr>
              </ng-template>
              <ng-template #footer>
                <tr class="mem-total">
                  <td>RBT12</td>
                  <td style="text-align:right"><strong>{{ reais(m.rbt12Centavos) }}</strong></td>
                  <td>Imposto {{ competenciaLabel(m.competencia) }}: {{ reais(m.impostoCentavos) }}</td>
                </tr>
              </ng-template>
            </p-table>
          </div>
        } @else {
          <p class="vazio">Sem memória para a competência selecionada.</p>
        }
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .hint { margin: 0 0 1.25rem; font-size: .85rem; color: var(--text-color-secondary); max-width: 52rem; }
    .fis-cols { display: flex; gap: 1.5rem; flex-wrap: wrap; align-items: flex-start; }
    .fis-card { flex: 1 1 22rem; border: 1px solid var(--surface-border); border-radius: 10px; padding: 1.1rem 1.25rem; background: var(--surface-0); }
    .fis-card.mem { flex-basis: 100%; margin-top: 1.5rem; }
    .fis-card h4 { margin: 0 0 1rem; font-size: 1rem; }
    .fis-card h5 { margin: 1.25rem 0 .5rem; font-size: .9rem; }
    .fis-form { display: flex; flex-direction: column; gap: 1rem; }
    .dois { display: flex; gap: 1rem; flex-wrap: wrap; }
    .dois .field { flex: 1 1 10rem; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .fis-form .linha { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
    .fis-form .linha label { font-size: .8rem; color: var(--text-color-secondary); }
    .w-full { width: 100%; }
    .hint2 { margin: 0 0 .75rem; font-size: .78rem; color: var(--text-color-secondary); }
    .hist { margin-top: 1.25rem; border-top: 1px dashed var(--surface-border); padding-top: 1rem; }
    .hist-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(11rem, 1fr)); gap: .6rem; }
    .hist-linha { display: flex; flex-direction: column; gap: .25rem; }
    .hist-linha label { font-size: .72rem; color: var(--text-color-secondary); }
    .hist-total { display: flex; justify-content: space-between; align-items: center; margin: .9rem 0; padding-top: .6rem; border-top: 2px solid var(--surface-border); }
    .fis-ap .linha { display: flex; justify-content: space-between; gap: 1rem; padding: .45rem 0; border-bottom: 1px solid var(--surface-border); }
    .fis-ap .total { border-bottom: none; border-top: 2px solid var(--surface-border); margin-top: .25rem; padding-top: .6rem; }
    .fis-ap .total strong { font-size: 1.15rem; color: var(--primary-color); }
    .fis-ap small { color: var(--text-color-secondary); font-weight: 400; }
    .obs { margin: .75rem 0 0; font-size: .78rem; color: var(--text-color-secondary); }
    .alerta { color: var(--red-600, #dc2626); font-size: .8rem; margin: .5rem 0 0; }
    .vazio { color: var(--text-color-secondary); }
    .mem-head { display: flex; align-items: center; justify-content: space-between; gap: 1rem; flex-wrap: wrap; margin-bottom: .5rem; }
    .mem-head h4 { margin: 0; }
    .mem-controls { display: flex; align-items: center; gap: .5rem; flex-wrap: wrap; }
    .passos { display: flex; flex-direction: column; gap: .5rem; margin: .5rem 0 1rem; }
    .passo { display: grid; grid-template-columns: 1fr auto; gap: .1rem 1rem; padding: .5rem .75rem; border: 1px solid var(--surface-border); border-radius: 8px; background: var(--surface-50, var(--surface-0)); }
    .passo-t { font-weight: 600; font-size: .9rem; }
    .passo-t .ord { display: inline-flex; align-items: center; justify-content: center; width: 1.25rem; height: 1.25rem; margin-right: .5rem; border-radius: 50%; background: var(--primary-color); color: var(--primary-contrast-color, #fff); font-size: .72rem; }
    .passo-f { grid-column: 1; font-size: .8rem; color: var(--text-color-secondary); }
    .passo-v { grid-column: 2; grid-row: 1 / span 2; align-self: center; font-weight: 700; font-variant-numeric: tabular-nums; }
    .mem-total td { font-weight: 600; }
  `],
})
export class ConfigFiscalTabComponent implements OnInit {
  private service = inject(ContabilidadeService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  salvando = signal(false);
  salvandoHist = signal(false);
  apuracao = signal<ApuracaoFiscal | null>(null);
  memoria = signal<MemoriaFiscal | null>(null);
  memoriaAberta = signal(true);

  anexos = [
    { label: 'Automático por Fator R (III/V)', value: 'AUTO' },
    { label: 'Anexo III (serviços)', value: 'III' },
    { label: 'Anexo V (serviços)', value: 'V' },
    { label: 'Anexo I (comércio)', value: 'I' },
    { label: 'Anexo II (indústria)', value: 'II' },
  ];

  cfg: FiscalConfig = { regime: 'simples_nacional', calculoAutomatico: false, anexo: 'AUTO', dataInicioAtividade: null, dataEntradaSistema: null, folha12mCentavos: 0 };
  inicio: Date | null = null;
  corte: Date | null = null;
  folhaReais = 0;
  historico: { competencia: string; reais: number }[] = [];
  competenciaMes: Date = new Date(new Date().getFullYear(), new Date().getMonth(), 1);

  ngOnInit(): void {
    this.service.fiscalConfig().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (c) => {
        this.cfg = c;
        this.folhaReais = (c.folha12mCentavos ?? 0) / 100;
        this.inicio = this.parseData(c.dataInicioAtividade);
        this.corte = this.parseData(c.dataEntradaSistema ?? null);
        this.regenerarGrade();
        this.carregarHistorico();
      },
      error: () => {},
    });
    this.carregarApuracao();
    this.carregarMemoria();
  }

  salvar(): void {
    const dto: FiscalConfig = {
      regime: this.cfg.regime || 'simples_nacional',
      calculoAutomatico: !!this.cfg.calculoAutomatico,
      anexo: this.cfg.anexo || 'AUTO',
      dataInicioAtividade: this.inicio ? this.iso(this.inicio) : null,
      dataEntradaSistema: this.corte ? this.iso(this.corte) : null,
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
        this.carregarMemoria();
      },
      error: (err) => this.msg.add({ severity: 'error', summary: 'Fiscal',
        detail: err?.error?.mensagem || err?.error?.message || err?.error?.error || 'Falha ao salvar a configuração fiscal.' }),
    });
  }

  /** Regenera as 12 competências [corte-12 .. corte-1], preservando os valores já digitados. */
  regenerarGrade(): void {
    if (!this.corte) { this.historico = []; return; }
    const anteriores = new Map(this.historico.map((h) => [h.competencia, h.reais]));
    const base = new Date(this.corte.getFullYear(), this.corte.getMonth(), 1);
    const linhas: { competencia: string; reais: number }[] = [];
    for (let i = 12; i >= 1; i--) {
      const d = new Date(base.getFullYear(), base.getMonth() - i, 1);
      const comp = this.ym(d);
      linhas.push({ competencia: comp, reais: anteriores.get(comp) ?? 0 });
    }
    this.historico = linhas;
  }

  private carregarHistorico(): void {
    this.service.receitaHistorica().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (itens: ReceitaHistoricaItem[]) => {
        const mapa = new Map(itens.map((i) => [i.competencia, i.receitaBrutaCentavos]));
        for (const h of this.historico) {
          if (mapa.has(h.competencia)) h.reais = (mapa.get(h.competencia) ?? 0) / 100;
        }
        // dispara change detection recriando a referência do array
        this.historico = [...this.historico];
      },
      error: () => {},
    });
  }

  salvarHistorico(): void {
    const itens: ReceitaHistoricaItem[] = this.historico.map((h) => ({
      competencia: h.competencia,
      receitaBrutaCentavos: Math.round((h.reais ?? 0) * 100),
    }));
    this.salvandoHist.set(true);
    this.service.salvarReceitaHistorica(itens).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.salvandoHist.set(false)),
    ).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Fiscal', detail: 'Receita histórica salva.' });
        this.carregarApuracao();
        this.carregarMemoria();
      },
      error: (err) => this.msg.add({ severity: 'error', summary: 'Fiscal',
        detail: err?.error?.mensagem || err?.error?.message || 'Falha ao salvar a receita histórica.' }),
    });
  }

  totalHistoricoCentavos(): number {
    return this.historico.reduce((s, h) => s + Math.round((h.reais ?? 0) * 100), 0);
  }

  carregarMemoria(): void {
    this.service.fiscalMemoria(this.ym(this.competenciaMes)).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (m) => this.memoria.set(m),
      error: () => this.memoria.set(null),
    });
  }

  private carregarApuracao(): void {
    this.service.fiscalApuracao().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (a) => this.apuracao.set(a),
      error: () => this.apuracao.set(null),
    });
  }

  imprimir(): void {
    const m = this.memoria();
    if (!m) return;
    const w = window.open('', '_blank', 'width=820,height=920');
    if (!w) {
      this.msg.add({ severity: 'warn', summary: 'Impressão', detail: 'Permita pop-ups para imprimir a memória.' });
      return;
    }
    w.document.write(this.htmlImpressao(m));
    w.document.close();
    w.focus();
    w.print();
  }

  reais(c: number): string {
    return (c / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  competenciaLabel(ym: string): string {
    const [y, m] = ym.split('-');
    return `${m}/${y}`;
  }

  private ym(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  private iso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private parseData(s: string | null): Date | null {
    if (!s) return null;
    const [y, m, d] = s.split('-').map(Number);
    return new Date(y, m - 1, d);
  }

  private pct(v: number): string {
    return `${(v ?? 0).toFixed(2).replace('.', ',')}%`;
  }

  private esc(s: string): string {
    return (s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  private htmlImpressao(m: MemoriaFiscal): string {
    const passos = m.passos.map((p) =>
      `<tr><td class="ord">${p.ordem}</td><td><b>${this.esc(p.titulo)}</b><div class="f">${this.esc(p.formula)}</div></td><td class="v">${this.esc(p.valor)}</td></tr>`).join('');
    const janela = m.janela.map((i) =>
      `<tr><td>${this.competenciaLabel(i.competencia)}</td><td class="r">${this.reais(i.receitaCentavos)}</td><td>${this.esc(i.fonte)}</td></tr>`).join('');
    return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
      <title>Memória de cálculo ${this.competenciaLabel(m.competencia)}</title>
      <style>
        body { font-family: Arial, Helvetica, sans-serif; color: #111; margin: 28px; }
        h1 { font-size: 18px; margin: 0; }
        h2 { font-size: 14px; margin: 22px 0 6px; }
        .head { display: flex; justify-content: space-between; align-items: baseline; border-bottom: 2px solid #333; padding-bottom: 8px; }
        .muted { color: #555; font-size: 12px; }
        table { border-collapse: collapse; width: 100%; margin-top: 4px; }
        th, td { border: 1px solid #ccc; padding: 6px 8px; font-size: 12px; text-align: left; vertical-align: top; }
        td.v, td.r, th.r { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
        td.ord { width: 26px; text-align: center; color: #666; }
        .f { color: #555; font-size: 11px; margin-top: 2px; }
      </style></head><body>
      <div class="head"><h1>Memória de cálculo — Simples Nacional</h1>
        <div class="muted">Competência ${this.competenciaLabel(m.competencia)}</div></div>
      <p class="muted">Regime: ${this.esc(m.regime)} · Anexo ${this.esc(m.anexoEfetivo)} · Faixa ${m.faixa ?? '—'} ·
        Alíquota efetiva ${this.pct(m.aliquotaEfetiva)} · RBT12 ${this.reais(m.rbt12Centavos)}</p>
      <h2>Passos</h2>
      <table><tbody>${passos}</tbody></table>
      <h2>Janela do RBT12 — ${m.mesesAtividade} ${m.mesesAtividade === 1 ? 'mês' : 'meses'}${m.proporcionalizado ? ' (proporcionalizado)' : ''}</h2>
      <table><thead><tr><th>Competência</th><th class="r">Receita</th><th>Fonte</th></tr></thead>
        <tbody>${janela}</tbody>
        <tfoot><tr><td><b>RBT12</b></td><td class="r"><b>${this.reais(m.rbt12Centavos)}</b></td><td>Imposto: ${this.reais(m.impostoCentavos)}</td></tr></tfoot>
      </table>
      </body></html>`;
  }
}
