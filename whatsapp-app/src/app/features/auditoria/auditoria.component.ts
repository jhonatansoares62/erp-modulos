import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CardModule } from 'primeng/card';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { AuditoriaAcesso, WhatsAppApiService } from '../../core/whatsapp-api.service';

/**
 * Trilha de auditoria de acesso (LGPD item 3): tabela read-only, paginada, do
 * GET /api/whatsapp/auditoria — quem (atendente) abriu o histórico de qual paciente
 * (telefone) e quando. A lista de conversas (polling) não é auditada; abrir um chat
 * é registrado 1x por janela (dedup no backend).
 */
@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [CardModule, TableModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-card>
      <ng-template #title><i class="pi pi-history"></i> Trilha de auditoria de acesso</ng-template>
      <p class="ajuda">
        Registro de <strong>quem acessou o dado de quem</strong> no inbox (LGPD): abrir o histórico
        de um paciente, assumir ou encerrar um atendimento. Abrir o chat é registrado uma vez por
        janela (o polling não gera ruído). Mais recente primeiro.
      </p>

      <p-table [value]="registros()" [lazy]="true" (onLazyLoad)="carregar($event)"
               [paginator]="true" [rows]="rows" [totalRecords]="total()" [loading]="loading()"
               [rowsPerPageOptions]="[50, 100, 200]" styleClass="p-datatable-sm">
        <ng-template #header>
          <tr>
            <th style="width: 12rem">Quando</th>
            <th>Atendente</th>
            <th style="width: 10rem">Ação</th>
            <th style="width: 12rem">Paciente (telefone)</th>
          </tr>
        </ng-template>
        <ng-template #body let-r>
          <tr>
            <td>{{ fmtData(r.criadoEm) }}</td>
            <td>{{ r.atendenteEmail || '— (sistema)' }}</td>
            <td><p-tag [value]="rotuloAcao(r.acao)" [severity]="severidade(r.acao)" /></td>
            <td>{{ r.telefoneAlvo || '—' }}</td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr><td colspan="4" class="vazio">Sem acessos registrados ainda.</td></tr>
        </ng-template>
      </p-table>
    </p-card>
  `,
  styles: [`
    :host { display: block; }
    .ajuda { color: var(--text-color-secondary); font-size: .9rem; margin-bottom: 1.25rem; line-height: 1.5; }
    .vazio { text-align: center; color: var(--text-color-secondary); padding: 1.5rem; }
  `],
})
export class AuditoriaComponent {
  private api = inject(WhatsAppApiService);
  private destroyRef = inject(DestroyRef);

  loading = signal(false);
  registros = signal<AuditoriaAcesso[]>([]);
  total = signal(0);
  readonly rows = 50;

  /** p-table [lazy] dispara onLazyLoad no init e a cada mudança de página. */
  carregar(event: TableLazyLoadEvent): void {
    const size = event.rows ?? this.rows;
    const page = Math.floor((event.first ?? 0) / size);
    this.loading.set(true);
    this.api.auditoria(page, size)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (pg) => {
          this.registros.set(pg.content);
          this.total.set(pg.totalElements);
        },
        error: () => {
          this.registros.set([]);
          this.total.set(0);
        },
      });
  }

  rotuloAcao(acao: string): string {
    switch (acao) {
      case 'abriu_chat': return 'abriu o chat';
      case 'assumiu': return 'assumiu';
      case 'encerrou': return 'encerrou';
      default: return acao;
    }
  }

  severidade(acao: string): 'info' | 'success' | 'warn' {
    switch (acao) {
      case 'assumiu': return 'success';
      case 'encerrou': return 'warn';
      default: return 'info';
    }
  }

  fmtData(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? iso : d.toLocaleString('pt-BR');
  }
}
