import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { finalize } from 'rxjs';
import { Conta, ContabilService, Razao } from '../relatorios/contabil.service';

/** Razão por conta: extrato cronológico de uma conta analítica com saldo acumulado. */
@Component({
  selector: 'app-razao',
  standalone: true,
  imports: [FormsModule, DatePipe, DatePickerModule, SelectModule, TableModule, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card">
      <h3>Razão por conta</h3>
      <div class="rz-filtros">
        <div class="field field--conta">
          <label>Conta</label>
          <p-select [(ngModel)]="conta" [options]="contas()" optionLabel="rotulo" optionValue="codigo"
                    [filter]="true" filterBy="rotulo" placeholder="Selecione a conta analítica"
                    appendTo="body" styleClass="w-full" />
        </div>
        <div class="field">
          <label>De</label>
          <p-datepicker [(ngModel)]="de" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
        </div>
        <div class="field">
          <label>Até</label>
          <p-datepicker [(ngModel)]="ate" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
        </div>
        <p-button label="Gerar" icon="pi pi-search" [loading]="loading()" (onClick)="gerar()" />
        @if (razao()) {
          <p-button label="CSV" icon="pi pi-download" severity="secondary" (onClick)="exportarCsv()" />
        }
      </div>

      @if (razao(); as r) {
        <div class="rz-cab">
          <h4><code>{{ r.codigo }}</code> {{ r.nome }}</h4>
          <span>Saldo inicial: <b>{{ reais(r.saldoInicialCentavos) }}</b></span>
        </div>
        <p-table [value]="r.linhas" styleClass="p-datatable-sm" [scrollable]="true" scrollHeight="480px">
          <ng-template #header>
            <tr>
              <th style="width:100px">Data</th>
              <th style="width:70px">Nº</th>
              <th>Histórico</th>
              <th style="text-align:right">Débito</th>
              <th style="text-align:right">Crédito</th>
              <th style="text-align:right">Saldo</th>
            </tr>
          </ng-template>
          <ng-template #body let-l>
            <tr>
              <td>{{ l.data | date:'dd/MM/yyyy' }}</td>
              <td>{{ l.numero }}</td>
              <td>{{ l.historico }}</td>
              <td style="text-align:right">{{ l.tipo === 'D' ? reais(l.valorCentavos) : '' }}</td>
              <td style="text-align:right">{{ l.tipo === 'C' ? reais(l.valorCentavos) : '' }}</td>
              <td style="text-align:right">{{ reais(l.saldoAcumuladoCentavos) }}</td>
            </tr>
          </ng-template>
          <ng-template #footer>
            <tr class="rz-total">
              <td colspan="5">Saldo final</td>
              <td style="text-align:right">{{ reais(r.saldoFinalCentavos) }}</td>
            </tr>
          </ng-template>
          <ng-template #emptymessage>
            <tr><td colspan="6" class="vazio">Sem movimentos no período.</td></tr>
          </ng-template>
        </p-table>
      } @else {
        <p class="vazio">Selecione a conta e o período e clique em Gerar.</p>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .card { background: var(--surface-card); border: 1px solid var(--surface-border);
      border-radius: var(--content-border-radius); padding: 1.25rem 1.5rem; }
    .card h3 { margin: 0 0 1rem; font-size: 1rem; }
    .rz-filtros { display: flex; align-items: flex-end; gap: 1rem; margin-bottom: 1.25rem; flex-wrap: wrap; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field--conta { min-width: 22rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    :host ::ng-deep .w-full { width: 100%; }
    .rz-cab { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: .5rem; flex-wrap: wrap; gap: .5rem; }
    .rz-cab h4 { margin: 0; font-size: 1rem; }
    .rz-total td { font-weight: 700; }
    .vazio { color: var(--text-color-secondary); }
  `],
})
export class RazaoComponent implements OnInit {
  private service = inject(ContabilService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  loading = signal(false);
  contas = signal<(Conta & { rotulo: string })[]>([]);
  razao = signal<Razao | null>(null);

  conta = '';
  de: Date = new Date(new Date().getFullYear(), 0, 1);
  ate: Date = new Date(new Date().getFullYear(), 11, 31);

  ngOnInit(): void {
    this.service.listarContas().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (cs) => this.contas.set(cs.filter((c) => c.aceitaLancamento)
        .map((c) => ({ ...c, rotulo: c.codigo + ' — ' + c.nome }))),
      error: () => {},
    });
  }

  gerar(): void {
    if (!this.conta) {
      this.msg.add({ severity: 'warn', summary: 'Razão', detail: 'Selecione uma conta.' });
      return;
    }
    this.loading.set(true);
    this.service.razao(this.conta, this.iso(this.de), this.iso(this.ate)).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (r) => this.razao.set(r),
      error: () => this.msg.add({ severity: 'error', summary: 'Razão', detail: 'Falha ao gerar o razão.' }),
    });
  }

  exportarCsv(): void {
    const r = this.razao();
    if (!r) return;
    const linhas = [
      ['Data', 'Numero', 'Historico', 'Debito', 'Credito', 'Saldo'].join(';'),
      ['', '', 'Saldo inicial', '', '', this.num(r.saldoInicialCentavos)].join(';'),
      ...r.linhas.map((l) => [
        l.data, l.numero, this.csv(l.historico),
        l.tipo === 'D' ? this.num(l.valorCentavos) : '',
        l.tipo === 'C' ? this.num(l.valorCentavos) : '',
        this.num(l.saldoAcumuladoCentavos),
      ].join(';')),
      ['', '', 'Saldo final', '', '', this.num(r.saldoFinalCentavos)].join(';'),
    ];
    this.baixar(`razao_${r.codigo}_${this.iso(this.de)}_${this.iso(this.ate)}.csv`, linhas.join('\n'));
  }

  reais(c: number): string {
    return (c / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
  private num(c: number): string { return (c / 100).toFixed(2).replace('.', ','); }
  private csv(s: string | null): string { return s ? '"' + s.replace(/"/g, '""') + '"' : ''; }
  private baixar(nome: string, conteudo: string): void {
    const blob = new Blob(['﻿' + conteudo], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = nome; a.click();
    URL.revokeObjectURL(url);
  }
  private iso(d: Date | null): string {
    if (!d) return '';
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
