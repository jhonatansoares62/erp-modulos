import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService } from 'primeng/api';
import { finalize, forkJoin } from 'rxjs';
import { PRIMENG_MODULES } from '../../shared/primeng';
import { BotaoComponent } from '../../shared/components/botao/botao.component';
import { AuthService } from '../../core/auth.service';
import {
  ContabilidadeService,
  EncerramentoPreview,
  EncerramentoResultado,
  PeriodoFechado,
  ReaberturaResultado,
} from '../../shared/services/contabilidade.service';

/**
 * Encerramento de exercício (ITG 1000 / CFC): zera as contas de resultado (receitas/custos/
 * despesas) contra a ARE (3.9.9.01) e transfere o resultado para Lucros (2.3.3.01) ou Prejuízos
 * (2.3.3.02). O contador escolhe o ano, confere o preview (sem postar) e confirma. Um exercício
 * encerra uma vez só — reabrir a tela mostra o ano como encerrado e bloqueia novo encerramento.
 */
@Component({
  selector: 'app-encerramento',
  standalone: true,
  imports: [FormsModule, DatePipe, ...PRIMENG_MODULES, BotaoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card">
      <div class="enc-top">
        <div class="field">
          <label>Exercício (ano)</label>
          <p-select [(ngModel)]="ano" [options]="anos" optionLabel="label" optionValue="value"
                    appendTo="body" styleClass="w-full" (onChange)="carregar()" />
        </div>
        <app-botao label="Atualizar" icon="pi pi-refresh" [text]="true" size="small" (clicado)="carregar()" />
      </div>

      @if (preview(); as p) {
        <div class="enc-preview">
          <div class="linha"><span>Receitas do exercício</span><strong>{{ reais(p.receitas) }}</strong></div>
          <div class="linha"><span>(−) Custos</span><strong>{{ reais(p.custos) }}</strong></div>
          <div class="linha"><span>(−) Despesas</span><strong>{{ reais(p.despesas) }}</strong></div>
          <div class="linha total" [class.lucro]="p.resultado >= 0" [class.prejuizo]="p.resultado < 0">
            <span>{{ p.resultado >= 0 ? 'Lucro do exercício' : 'Prejuízo do exercício' }}</span>
            <strong>{{ reais(p.resultado) }}</strong>
          </div>
        </div>

        @if (p.encerrado) {
          <div class="enc-reabrir">
            <p-tag severity="success" icon="pi pi-check-circle" [value]="'Exercício ' + p.ano + ' encerrado'" styleClass="enc-tag" />
            <app-botao label="Reabrir exercício" icon="pi pi-lock-open" severity="danger" [text]="true" size="small"
                       [loading]="reabrindo()" (clicado)="confirmarReabrir()" />
          </div>
          <span class="enc-hint">Reabrir reverte os lançamentos de encerramento, destrava {{ p.ano }} e reprocessa as
            pendências presas em período fechado — depois é preciso RE-ENCERRAR.</span>
        } @else if (!temMovimento()) {
          <div class="enc-aviso"><i class="pi pi-info-circle"></i>
            <span>Sem movimento de resultado em {{ p.ano }} — nada a encerrar.</span></div>
        } @else {
          <div class="enc-acao">
            <app-botao label="Encerrar exercício" icon="pi pi-lock" [loading]="encerrando()"
                       [disabled]="!podeEncerrar()" (clicado)="confirmarEncerrar()" />
            <span class="enc-hint">Posta os lançamentos de 31/12/{{ p.ano }} zerando resultado contra a ARE
              e transferindo {{ p.resultado >= 0 ? 'o lucro para Lucros (2.3.3.01)' : 'o prejuízo para Prejuízos (2.3.3.02)' }}.</span>
          </div>
        }
      }

      @if (gerados().length) {
        <div class="enc-gerados">
          <i class="pi pi-check"></i>
          <span>Lançamentos gerados: {{ gerados().join(', ') }}</span>
        </div>
      }
    </div>

    <div class="card">
      <h3>Exercícios encerrados</h3>
      @if (encerrados().length) {
        <p-table [value]="encerrados()" styleClass="p-datatable-sm">
          <ng-template #header>
            <tr>
              <th style="width:120px">Exercício</th>
              <th style="width:160px">Encerrado em</th>
              <th>Por</th>
            </tr>
          </ng-template>
          <ng-template #body let-e>
            <tr>
              <td><code>{{ e.competencia }}</code></td>
              <td>{{ e.dataFechamento | date:'dd/MM/yyyy HH:mm' }}</td>
              <td>{{ e.fechadoPor || '—' }}</td>
            </tr>
          </ng-template>
        </p-table>
      } @else {
        <p class="vazio">Nenhum exercício encerrado ainda.</p>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .card { background: var(--surface-card); border: 1px solid var(--surface-border);
      border-radius: var(--content-border-radius); padding: 1.25rem 1.5rem; margin-bottom: 1.25rem; }
    .card h3 { margin: 0 0 1rem; font-size: 1rem; }
    .enc-top { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem; margin-bottom: 1.25rem; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    :host ::ng-deep .w-full { width: 100%; }
    .enc-preview { border: 1px solid var(--surface-border); border-radius: 8px; overflow: hidden; margin-bottom: 1rem; }
    .enc-preview .linha { display: flex; justify-content: space-between; padding: .6rem .9rem; font-size: .92rem; }
    .enc-preview .linha:nth-child(odd) { background: var(--surface-ground); }
    .enc-preview .total { border-top: 2px solid var(--surface-border); font-size: 1.05rem; font-weight: 700; }
    .enc-preview .total.lucro strong { color: var(--color-success, #16a34a); }
    .enc-preview .total.prejuizo strong { color: var(--color-danger, #dc2626); }
    :host ::ng-deep .enc-tag { font-size: .95rem; padding: .5rem .8rem; }
    .enc-reabrir { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; margin-bottom: .4rem; }
    .enc-acao { display: flex; flex-direction: column; gap: .5rem; }
    .enc-hint { font-size: .82rem; color: var(--text-color-secondary); }
    .enc-aviso { display: flex; align-items: center; gap: .6rem; padding: .6rem .9rem; border: 1px solid var(--surface-border);
      border-radius: 8px; background: var(--surface-ground); font-size: .88rem; }
    .enc-aviso i { color: var(--color-info); }
    .enc-gerados { display: flex; align-items: center; gap: .6rem; margin-top: 1rem; padding: .6rem .9rem;
      border: 1px solid var(--surface-border); border-radius: 8px; background: var(--surface-ground); font-size: .88rem; }
    .enc-gerados i { color: var(--color-success, #16a34a); }
    .vazio { color: var(--text-color-secondary); }
  `],
})
export class EncerramentoComponent implements OnInit {
  private service = inject(ContabilidadeService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);
  private confirm = inject(ConfirmationService);
  private auth = inject(AuthService);

  preview = signal<EncerramentoPreview | null>(null);
  encerrados = signal<PeriodoFechado[]>([]);
  gerados = signal<number[]>([]);
  encerrando = signal(false);
  reabrindo = signal(false);

  ano: number = new Date().getFullYear();
  readonly anos = Array.from({ length: 6 }, (_, i) => {
    const y = new Date().getFullYear() - i;
    return { label: String(y), value: y };
  });

  temMovimento = computed(() => {
    const p = this.preview();
    return !!p && (p.receitas !== 0 || p.custos !== 0 || p.despesas !== 0);
  });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.gerados.set([]);
    forkJoin({
      preview: this.service.encerramentoPreview(this.ano),
      periodos: this.service.periodos(),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ preview, periodos }) => {
        this.preview.set(preview);
        this.encerrados.set(periodos.filter((p) => p.tipo === 'exercicio'));
      },
      error: () => this.msg.add({ severity: 'error', summary: 'Encerramento', detail: 'Falha ao carregar o exercício.' }),
    });
  }

  podeEncerrar(): boolean {
    const p = this.preview();
    return !!p && !p.encerrado && this.temMovimento() && !this.encerrando();
  }

  confirmarEncerrar(): void {
    if (!this.podeEncerrar()) return;
    const p = this.preview()!;
    const rotulo = p.resultado >= 0 ? 'lucro' : 'prejuízo';
    this.confirm.confirm({
      header: `Encerrar exercício ${p.ano}`,
      message: `Encerrar o exercício de ${p.ano}? Isso zera as receitas, custos e despesas contra a ARE (3.9.9.01) `
        + `e transfere o ${rotulo} de ${this.reais(Math.abs(p.resultado))} para `
        + `${p.resultado >= 0 ? 'Lucros Acumulados (2.3.3.01)' : 'Prejuízos Acumulados (2.3.3.02)'}. `
        + `O exercício só encerra uma vez.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Encerrar',
      rejectLabel: 'Cancelar',
      accept: () => this.encerrar(),
    });
  }

  private encerrar(): void {
    this.encerrando.set(true);
    this.service.encerrarExercicio(this.ano, this.auth.user()?.nome)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.encerrando.set(false)))
      .subscribe({
        next: (r: EncerramentoResultado) => {
          this.gerados.set(r.lancamentoIds ?? []);
          this.msg.add({ severity: 'success', summary: 'Exercício encerrado',
            detail: `Exercício ${r.ano} encerrado. Resultado: ${this.reais(r.resultado)}.` });
          this.carregar();
        },
        error: (err) => this.msg.add({ severity: 'error', summary: 'Encerramento',
          detail: err?.error?.detalhe || err?.error?.mensagem || err?.error?.message
            || (err?.status === 409 ? 'Exercício já encerrado.' : 'Falha ao encerrar o exercício.') }),
      });
  }

  confirmarReabrir(): void {
    const p = this.preview();
    if (!p || !p.encerrado || this.reabrindo()) return;
    this.confirm.confirm({
      header: `Reabrir exercício ${p.ano}`,
      message: `Reabrir o exercício de ${p.ano}? Isso REVERTE os lançamentos de encerramento (a ARE volta a `
        + `zerar e o resultado deixa de ser transportado ao PL), destrava o ano e reprocessa as pendências `
        + `presas em período fechado — que passam a postar. Depois será preciso RE-ENCERRAR ${p.ano}.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Reabrir',
      rejectLabel: 'Cancelar',
      accept: () => this.reabrir(),
    });
  }

  private reabrir(): void {
    this.reabrindo.set(true);
    this.service.reabrirExercicio(this.ano, this.auth.user()?.nome, 'Reabertura via tela de Encerramento')
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.reabrindo.set(false)))
      .subscribe({
        next: (r: ReaberturaResultado) => {
          this.msg.add({ severity: 'success', summary: 'Exercício reaberto',
            detail: `Exercício ${r.ano} reaberto: ${r.lancamentosEstornados} lançamento(s) de encerramento `
              + `revertido(s), ${r.pendenciasReprocessadas} pendência(s) reprocessada(s). Re-encerre após ajustar.` });
          this.carregar();
        },
        error: (err) => this.msg.add({ severity: 'error', summary: 'Reabertura',
          detail: err?.error?.detalhe || err?.error?.mensagem || err?.error?.message
            || (err?.status === 409 ? 'Exercício não está encerrado.' : 'Falha ao reabrir o exercício.') }),
      });
  }

  reais(centavos: number): string {
    return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
