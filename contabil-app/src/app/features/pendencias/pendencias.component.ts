import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { finalize } from 'rxjs';
import { PRIMENG_MODULES } from '../../shared/primeng';
import { BotaoComponent } from '../../shared/components/botao/botao.component';
import { Conta, ContabilidadeService, Pendencia, RegraCreate } from '../../shared/services/contabilidade.service';

/**
 * Fila de pendências (eventos sem roteiro). Ao classificar uma pendência, o contador
 * define o roteiro (débito/crédito); o módulo cria a regra e reprocessa o evento
 * guardado — "salvar como regra": classifica uma vez, automatiza para sempre.
 */
@Component({
  selector: 'app-config-pendencias-tab',
  standalone: true,
  imports: [FormsModule, ...PRIMENG_MODULES, BotaoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pend-head">
      <p class="pend-info">
        Eventos aguardando resolução. <strong>Sem roteiro</strong>: classifique uma vez e o módulo passa a
        contabilizar os próximos iguais. <strong>Período fechado</strong>: reabra o exercício (aba Encerramento)
        para o evento poder postar. O botão Reprocessar cobre os dois.
      </p>
      <div class="pend-acoes">
        <app-botao label="Reprocessar" icon="pi pi-sync" size="small" [loading]="reprocessando()"
                   (clicado)="reprocessar()" />
        <app-botao label="Atualizar" icon="pi pi-refresh" [text]="true" size="small" (clicado)="carregar()" />
      </div>
    </div>

    @if (loading()) {
      <p>Carregando pendências...</p>
    } @else if (pendencias().length === 0) {
      <p class="vazio">Nenhuma pendência. Tudo contabilizado.</p>
    } @else {
      <p-table [value]="pendencias()" [rows]="10" [paginator]="pendencias().length > 10" styleClass="p-datatable-sm">
        <ng-template #header>
          <tr><th>Evento</th><th>Origem</th><th>Situação</th><th>Data</th><th style="text-align:right">Valor</th><th></th></tr>
        </ng-template>
        <ng-template #body let-p>
          <tr>
            <td><code>{{ p.tipo }}</code></td>
            <td>{{ p.origem }}</td>
            <td>
              @if (p.status === 'periodo_fechado') {
                <p-tag severity="warn" icon="pi pi-lock" value="Período fechado" />
                @if (p.motivo) { <div class="pend-motivo">{{ p.motivo }}</div> }
              } @else {
                <p-tag severity="info" icon="pi pi-question-circle" value="Sem roteiro" />
              }
            </td>
            <td>{{ p.dataEvento }}</td>
            <td style="text-align:right">{{ reais(p.valorCentavos) }}</td>
            <td style="text-align:right">
              @if (p.status === 'periodo_fechado') {
                <span class="pend-dica-acao"><i class="pi pi-info-circle"></i> Reabra o exercício</span>
              } @else {
                <app-botao label="Classificar" icon="pi pi-check" size="small" (clicado)="abrir(p)" />
              }
            </td>
          </tr>
        </ng-template>
      </p-table>
    }

    <p-dialog [visible]="dialogVisivel()" (visibleChange)="dialogVisivel.set($event)"
              [modal]="true" header="Classificar pendência" [style]="{ width: '34rem' }">
      @if (atual(); as p) {
        <p class="dlg-evento">
          <code>{{ p.tipo }}</code> · {{ reais(p.valorCentavos) }} · {{ p.dataEvento }}
        </p>
        <div class="dlg-form">
          <div class="field">
            <label>Débito</label>
            <p-select [(ngModel)]="debitoConta" [options]="contasOpcoes()" optionLabel="label" optionValue="value"
                      [filter]="true" placeholder="Conta a debitar" appendTo="body" styleClass="w-full" />
          </div>
          <div class="field">
            <label>Crédito</label>
            <p-select [(ngModel)]="creditoConta" [options]="contasOpcoes()" optionLabel="label" optionValue="value"
                      [filter]="true" placeholder="Conta a creditar" appendTo="body" styleClass="w-full" />
          </div>
          <div class="field field--full">
            <label>Histórico (opcional)</label>
            <input pInputText [(ngModel)]="historico" placeholder="Ex.: Serviço {numero}" class="w-full" />
          </div>
        </div>
        <p class="dlg-dica">O lançamento usará o valor total do evento: D {{ debitoConta || '—' }} · C {{ creditoConta || '—' }}.</p>
      }
      <ng-template #footer>
        <app-botao label="Cancelar" [text]="true" severity="secondary" (clicado)="dialogVisivel.set(false)" />
        <app-botao label="Salvar regra e contabilizar" icon="pi pi-check" [loading]="salvando()"
                   [disabled]="!podeSalvar()" (clicado)="salvar()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    :host { display: block; }
    .pend-head { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: .75rem; }
    .pend-acoes { display: flex; align-items: center; gap: .5rem; }
    .pend-info { margin: 0; font-size: .85rem; color: var(--text-color-secondary); }
    .vazio { color: var(--text-color-secondary); }
    .pend-motivo { margin-top: .25rem; font-size: .78rem; color: var(--text-color-secondary); max-width: 22rem; }
    .pend-dica-acao { font-size: .8rem; color: var(--text-color-secondary); white-space: nowrap; }
    .pend-dica-acao .pi { font-size: .75rem; margin-right: .2rem; }
    .dlg-evento { margin: 0 0 1rem; }
    .dlg-form { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem 1.25rem; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field--full { grid-column: 1 / -1; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .dlg-dica { margin: 1rem 0 0; font-size: .8rem; color: var(--text-color-secondary); }
    .w-full { width: 100%; }
  `],
})
export class ConfigPendenciasTabComponent implements OnInit {
  private service = inject(ContabilidadeService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  loading = signal(true);
  salvando = signal(false);
  reprocessando = signal(false);
  pendencias = signal<Pendencia[]>([]);
  contas = signal<Conta[]>([]);
  dialogVisivel = signal(false);
  atual = signal<Pendencia | null>(null);

  debitoConta = '';
  creditoConta = '';
  historico = '';

  contasOpcoes = computed(() =>
    this.contas()
      .filter((c) => c.aceitaLancamento)
      .map((c) => ({ label: c.codigo + ' — ' + c.nome, value: c.codigo })),
  );

  ngOnInit(): void {
    this.carregar();
    this.service.listarContas().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (contas) => this.contas.set(contas),
      error: () => { /* selects ficam vazios; erro do plano já é sinalizado na outra aba */ },
    });
  }

  carregar(): void {
    this.loading.set(true);
    this.service.listarPendencias().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (p) => this.pendencias.set(p),
      error: () => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: 'Falha ao carregar pendências.' }),
    });
  }

  reprocessar(): void {
    this.reprocessando.set(true);
    this.service.reprocessar().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.reprocessando.set(false)),
    ).subscribe({
      next: (r) => {
        this.msg.add({
          severity: 'success',
          summary: 'Contabilidade',
          detail: `${r.reprocessados} de ${r.total} pendências contabilizadas`,
        });
        this.carregar();
      },
      error: (err) => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: err?.error?.mensagem || err?.error?.message || 'Falha ao reprocessar pendências.' }),
    });
  }

  abrir(p: Pendencia): void {
    this.atual.set(p);
    this.debitoConta = '';
    this.creditoConta = '';
    this.historico = '';
    this.dialogVisivel.set(true);
  }

  podeSalvar(): boolean {
    return !!this.debitoConta && !!this.creditoConta && this.debitoConta !== this.creditoConta;
  }

  salvar(): void {
    const p = this.atual();
    if (!p || !this.podeSalvar()) return;
    const regra: RegraCreate = {
      historicoTemplate: this.historico?.trim() || undefined,
      partidas: [
        { tipo: 'D', contaModo: 'constante', contaCodigo: this.debitoConta, base: 'valor_total' },
        { tipo: 'C', contaModo: 'constante', contaCodigo: this.creditoConta, base: 'valor_total' },
      ],
    };
    this.salvando.set(true);
    this.service.salvarComoRegra(p.eventoId, regra).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.salvando.set(false)),
    ).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Contabilidade', detail: 'Regra criada e evento contabilizado.' });
        this.dialogVisivel.set(false);
        this.carregar();
      },
      error: (err) => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: err?.error?.message || 'Falha ao salvar a regra.' }),
    });
  }

  reais(centavos: number): string {
    return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
