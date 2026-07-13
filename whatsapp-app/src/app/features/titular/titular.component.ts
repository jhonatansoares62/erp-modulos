import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { finalize } from 'rxjs';
import { ExportacaoTitular, WhatsAppApiService } from '../../core/whatsapp-api.service';

/**
 * DSAR (LGPD item 4): direitos do titular por telefone. Exportar (acesso) mostra o
 * histórico decifrado + baixa JSON; Esquecer (eliminação) anonimiza as mensagens e remove
 * os vínculos — com confirmação inline (sem diálogo nativo). A trilha de auditoria é mantida.
 */
@Component({
  selector: 'app-titular',
  standalone: true,
  imports: [FormsModule, CardModule, InputTextModule, ButtonModule, TableModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-card>
      <ng-template #title><i class="pi pi-id-card"></i> Direitos do titular (LGPD)</ng-template>
      <p class="ajuda">
        Exporte ou elimine os dados de um paciente por telefone (com DDI, ex.: 5546999999999).
        <strong>Esquecer</strong> anonimiza as mensagens e remove os vínculos — a trilha de
        auditoria é mantida como registro. Toda ação aqui é auditada.
      </p>

      <div class="linha">
        <input pInputText [(ngModel)]="telefone" placeholder="Telefone com DDI" autocomplete="off"
               (ngModelChange)="confirmando.set(false)" />
        <p-button label="Exportar" icon="pi pi-download" [loading]="exportando()"
                  [disabled]="!telefone.trim()" (onClick)="exportar()" />
        @if (!confirmando()) {
          <p-button label="Esquecer" icon="pi pi-trash" severity="danger" [outlined]="true"
                    [disabled]="!telefone.trim()" (onClick)="confirmando.set(true)" />
        } @else {
          <span class="confirma">Anonimizar tudo de {{ telefone }}?</span>
          <p-button label="Confirmar" icon="pi pi-check" severity="danger" [loading]="esquecendo()"
                    (onClick)="esquecer()" />
          <p-button label="Cancelar" [text]="true" severity="secondary" (onClick)="confirmando.set(false)" />
        }
      </div>

      @if (exportacao(); as e) {
        <div class="resultado">
          <p class="resumo">
            <strong>{{ e.mensagens.length }}</strong> mensagens ·
            idClienteErp: {{ e.idClienteErp ?? '—' }} ·
            <a class="baixar" (click)="baixar(e)">baixar JSON</a>
          </p>
          <p-table [value]="e.mensagens" styleClass="p-datatable-sm" [scrollable]="true" scrollHeight="360px">
            <ng-template #header>
              <tr><th style="width: 11rem">Quando</th><th style="width: 6rem">Direção</th>
                  <th style="width: 7rem">Tipo</th><th>Conteúdo</th></tr>
            </ng-template>
            <ng-template #body let-m>
              <tr><td>{{ fmt(m.timestamp) }}</td><td>{{ m.direcao }}</td>
                  <td>{{ m.tipo }}</td><td>{{ m.conteudo }}</td></tr>
            </ng-template>
            <ng-template #emptymessage>
              <tr><td colspan="4" class="vazio">Sem mensagens para esse telefone.</td></tr>
            </ng-template>
          </p-table>
        </div>
      }
    </p-card>
  `,
  styles: [`
    :host { display: block; }
    .ajuda { color: var(--text-color-secondary); font-size: .9rem; margin-bottom: 1.25rem; line-height: 1.5; }
    .linha { display: flex; flex-wrap: wrap; gap: .6rem; align-items: center; }
    .linha input { min-width: 16rem; }
    .confirma { font-size: .9rem; color: var(--red-600, #dc2626); font-weight: 500; }
    .resultado { margin-top: 1.5rem; }
    .resumo { font-size: .9rem; margin-bottom: .6rem; }
    .baixar { color: var(--primary-color); cursor: pointer; text-decoration: underline; }
    .vazio { text-align: center; color: var(--text-color-secondary); padding: 1.25rem; }
  `],
})
export class TitularComponent {
  private api = inject(WhatsAppApiService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  telefone = '';
  exportando = signal(false);
  esquecendo = signal(false);
  confirmando = signal(false);
  exportacao = signal<ExportacaoTitular | null>(null);

  exportar(): void {
    const tel = this.telefone.trim();
    if (!tel) return;
    this.exportando.set(true);
    this.api.exportarTitular(tel)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.exportando.set(false)))
      .subscribe({
        next: (e) => this.exportacao.set(e),
        error: () => this.msg.add({ severity: 'error', summary: 'Titular', detail: 'Falha ao exportar os dados.' }),
      });
  }

  esquecer(): void {
    const tel = this.telefone.trim();
    if (!tel) return;
    this.esquecendo.set(true);
    this.api.esquecerTitular(tel)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.esquecendo.set(false)))
      .subscribe({
        next: (r) => {
          this.confirmando.set(false);
          this.exportacao.set(null);
          this.msg.add({
            severity: 'success',
            summary: 'Titular esquecido',
            detail: `${r.mensagensAnonimizadas} mensagens anonimizadas`
              + (r.clienteRemovido ? ' · vínculo removido' : '') + '.',
          });
        },
        error: () => this.msg.add({ severity: 'error', summary: 'Titular', detail: 'Falha ao esquecer o titular.' }),
      });
  }

  baixar(e: ExportacaoTitular): void {
    const blob = new Blob([JSON.stringify(e, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `titular-${e.telefone}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  fmt(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? iso : d.toLocaleString('pt-BR');
  }
}
