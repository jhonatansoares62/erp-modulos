import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { ContabilService, Diario } from '../relatorios/contabil.service';

/** Livro Diário: lançamentos do período em ordem cronológica, cada um com suas partidas (D=C). */
@Component({
  selector: 'app-diario',
  standalone: true,
  imports: [FormsModule, DatePipe, DatePickerModule, TagModule, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card">
      <h3>Livro Diário</h3>
      <div class="di-filtros">
        <div class="field">
          <label>De</label>
          <p-datepicker [(ngModel)]="de" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
        </div>
        <div class="field">
          <label>Até</label>
          <p-datepicker [(ngModel)]="ate" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" />
        </div>
        <p-button label="Gerar" icon="pi pi-search" [loading]="loading()" (onClick)="gerar()" />
        @if (diario()) {
          <p-button label="CSV" icon="pi pi-download" severity="secondary" (onClick)="exportarCsv()" />
        }
      </div>

      @if (diario(); as d) {
        @if (d.lancamentos.length) {
          <div class="di-info">{{ d.lancamentos.length }} lançamentos no período.</div>
          @for (l of d.lancamentos; track l.numero) {
            <div class="lanc">
              <div class="lanc-cab">
                <span class="lanc-num">Nº {{ l.numero }}</span>
                <span class="lanc-data">{{ l.data | date:'dd/MM/yyyy' }}</span>
                <span class="lanc-hist">{{ l.historico }}</span>
                @if (l.balanceado) {
                  <p-tag value="D = C" severity="success" />
                } @else {
                  <p-tag value="Não balanceado" severity="danger" />
                }
              </div>
              <table class="lanc-partidas">
                @for (p of l.partidas; track $index) {
                  <tr>
                    <td class="pt-conta"><code>{{ p.codigo }}</code> {{ p.nome }}</td>
                    <td class="pt-deb">{{ p.tipo === 'D' ? reais(p.valorCentavos) : '' }}</td>
                    <td class="pt-cre">{{ p.tipo === 'C' ? reais(p.valorCentavos) : '' }}</td>
                  </tr>
                }
              </table>
            </div>
          }
        } @else {
          <p class="vazio">Nenhum lançamento no período.</p>
        }
      } @else {
        <p class="vazio">Informe o período e clique em Gerar.</p>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .card { background: var(--surface-card); border: 1px solid var(--surface-border);
      border-radius: var(--content-border-radius); padding: 1.25rem 1.5rem; }
    .card h3 { margin: 0 0 1rem; font-size: 1rem; }
    .di-filtros { display: flex; align-items: flex-end; gap: 1rem; margin-bottom: 1.25rem; flex-wrap: wrap; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .di-info { margin-bottom: .75rem; color: var(--text-color-secondary); font-size: .85rem; }
    .lanc { border: 1px solid var(--surface-border); border-radius: 8px; margin-bottom: .75rem; overflow: hidden; }
    .lanc-cab { display: flex; align-items: center; gap: .75rem; padding: .5rem .85rem; background: var(--surface-ground); flex-wrap: wrap; }
    .lanc-num { font-weight: 700; }
    .lanc-data { color: var(--text-color-secondary); }
    .lanc-hist { flex: 1; }
    .lanc-partidas { width: 100%; border-collapse: collapse; }
    .lanc-partidas td { padding: .3rem .85rem; border-top: 1px solid var(--surface-border); font-size: .9rem; }
    .pt-conta { width: 70%; }
    .pt-deb, .pt-cre { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .pt-cre { padding-left: 1.5rem; }
    .vazio { color: var(--text-color-secondary); }
  `],
})
export class DiarioComponent implements OnInit {
  private service = inject(ContabilService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  loading = signal(false);
  diario = signal<Diario | null>(null);

  de: Date = new Date(new Date().getFullYear(), 0, 1);
  ate: Date = new Date(new Date().getFullYear(), 11, 31);

  ngOnInit(): void {
    this.gerar();
  }

  gerar(): void {
    this.loading.set(true);
    this.service.diario(this.iso(this.de), this.iso(this.ate)).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (d) => this.diario.set(d),
      error: () => this.msg.add({ severity: 'error', summary: 'Livro Diário', detail: 'Falha ao gerar o diário.' }),
    });
  }

  exportarCsv(): void {
    const d = this.diario();
    if (!d) return;
    const linhas = [['Lancamento', 'Data', 'Historico', 'Conta', 'Debito', 'Credito'].join(';')];
    for (const l of d.lancamentos) {
      for (const p of l.partidas) {
        linhas.push([
          l.numero, l.data, this.csv(l.historico),
          this.csv(p.codigo + ' ' + p.nome),
          p.tipo === 'D' ? this.num(p.valorCentavos) : '',
          p.tipo === 'C' ? this.num(p.valorCentavos) : '',
        ].join(';'));
      }
    }
    this.baixar(`diario_${this.iso(this.de)}_${this.iso(this.ate)}.csv`, linhas.join('\n'));
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
