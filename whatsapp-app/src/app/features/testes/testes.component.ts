import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { interval } from 'rxjs';
import {
  Botao,
  Diagnostico,
  EnviarTeste,
  MonitorMensagem,
  WhatsAppApiService,
} from '../../core/whatsapp-api.service';

interface BotaoForm {
  id: string;
  title: string;
}

/**
 * Console de Testes — porta do protótipo /monitor.html. Feed ao vivo (polling 2s),
 * enviar teste (texto/botões) e diagnóstico. O status geral vem de
 * GET /api/whatsapp/status (Bearer); feed/enviar/diagnóstico usam /monitor/* — hoje
 * a tela DEPENDE desses endpoints de dev/meta (candidatos a migrar pra /api/whatsapp/*).
 * Nada quebra se um endpoint responder vazio (ex.: /monitor ausente em produção).
 */
@Component({
  selector: 'app-testes',
  standalone: true,
  imports: [DecimalPipe, FormsModule, CardModule, InputTextModule, TextareaModule, SelectButtonModule, ButtonModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="topo">
      <p-tag [value]="feedAoVivo() ? 'ao vivo' : 'sem conexão'"
             [severity]="feedAoVivo() ? 'success' : 'danger'"
             [icon]="feedAoVivo() ? 'pi pi-circle-fill' : 'pi pi-times-circle'" />
      <span class="badge">Número: <strong>{{ phoneNumberId() || '—' }}</strong></span>
      <span class="badge">Circuit: <strong [class]="'cb-' + circuitBreaker()">{{ circuitBreaker() }}</strong></span>
      <span class="badge">Total: <strong>{{ total() | number }}</strong></span>
    </div>

    <div class="layout">
      <aside class="painel">
        <p-card>
          <ng-template #title><i class="pi pi-send"></i> Enviar teste</ng-template>
          <div class="field">
            <label>Telefone (wa_id, com DDI 55 e o 9)</label>
            <input pInputText [(ngModel)]="telefone" placeholder="5546920009012" autocomplete="off" />
          </div>
          <div class="field">
            <label>Tipo</label>
            <p-selectButton [options]="tipos" [(ngModel)]="tipo" optionLabel="label" optionValue="value" [allowEmpty]="false" />
          </div>
          <div class="field">
            <label>Mensagem</label>
            <textarea pTextarea [(ngModel)]="texto" rows="3" [autoResize]="true"
                      placeholder="Olá! Mensagem de teste do painel."></textarea>
          </div>

          @if (tipo === 'botoes') {
            <div class="field">
              <label>Botões (id · título) — até 3</label>
              @for (b of botoes; track $index) {
                <div class="botao-row">
                  <input pInputText [(ngModel)]="b.id" placeholder="id" autocomplete="off" />
                  <input pInputText [(ngModel)]="b.title" placeholder="título" maxlength="20" autocomplete="off" />
                  <p-button icon="pi pi-times" severity="secondary" [text]="true"
                            [disabled]="botoes.length <= 1" (onClick)="removerBotao($index)" />
                </div>
              }
              @if (botoes.length < 3) {
                <p-button label="Adicionar botão" icon="pi pi-plus" severity="secondary" [text]="true"
                          size="small" (onClick)="adicionarBotao()" />
              }
            </div>
          }

          <p-button label="Enviar" icon="pi pi-send" [loading]="enviando()" [disabled]="!telefone.trim()"
                    styleClass="w-full" (onClick)="enviar()" />
          <small class="hint">Só entrega dentro da janela de 24h (o cliente precisa ter escrito antes).</small>
        </p-card>

        <p-card>
          <ng-template #title><i class="pi pi-wrench"></i> Diagnóstico</ng-template>
          <p-button label="Rodar diagnóstico" icon="pi pi-play" severity="secondary"
                    [loading]="diagnosticando()" styleClass="w-full" (onClick)="rodarDiagnostico()" />
          @if (diagnostico(); as d) {
            <div class="diag">
              <div class="diag-linha"><span class="k">Número</span><span class="v">{{ d.phoneNumberId || '—' }}</span></div>
              <div class="diag-linha"><span class="k">Graph API</span><span class="v">{{ d.metaApiBaseUrl }}</span></div>
              <div class="diag-linha"><span class="k">ERP callback</span><span class="v">{{ d.erpCallbackUrl }}</span></div>
              <div class="diag-linha"><span class="k">API key</span><span class="v">{{ d.apiKeyConfigurada ? 'configurada' : '(vazia)' }}</span></div>
              <div class="diag-linha"><span class="k">Circuit</span><span class="v" [class]="'cb-' + d.circuitBreakerState">{{ d.circuitBreakerState }}</span></div>
              <div class="diag-linha">
                <span class="k">Meta</span>
                <span class="v"><i class="pi" [class.pi-check-circle]="d.meta.ok" [class.pi-times-circle]="!d.meta.ok"
                  [class.ok]="d.meta.ok" [class.err]="!d.meta.ok"></i> {{ d.meta.detalhe }}</span>
              </div>
              <div class="diag-linha">
                <span class="k">ERP</span>
                <span class="v"><i class="pi" [class.pi-check-circle]="d.erp.ok" [class.pi-times-circle]="!d.erp.ok"
                  [class.ok]="d.erp.ok" [class.err]="!d.erp.ok"></i> {{ d.erp.detalhe }}</span>
              </div>
            </div>
          }
        </p-card>
      </aside>

      <section class="feed">
        <h3><i class="pi pi-comments"></i> Feed ao vivo</h3>
        @if (mensagens().length) {
          <div class="mensagens">
            @for (m of mensagens(); track m.id) {
              <div class="msg" [class.out]="m.direcao === 'out'" [class.in]="m.direcao !== 'out'"
                   (click)="responder(m)" title="Clique para responder a este número">
                <div class="msg-meta">
                  <span>{{ m.direcao === 'out' ? 'enviada' : 'recebida' }}</span>
                  <span class="tel">{{ m.telefone || '—' }}</span>
                  <span class="tipo">{{ m.tipo || '?' }}</span>
                </div>
                <div class="conteudo" [class.vazio]="!m.conteudo">{{ m.conteudo || '(sem texto)' }}</div>
                <div class="time">{{ hora(m.criadoEm) }}</div>
              </div>
            }
          </div>
        } @else {
          <p class="empty">Aguardando mensagens… envie algo pro número de teste.</p>
        }
      </section>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .topo { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.25rem; }
    .badge { font-size: .85rem; color: var(--text-color-secondary); }
    .badge strong { color: var(--text-color); }
    .cb-CLOSED { color: var(--color-success); }
    .cb-OPEN { color: var(--color-danger); }
    .cb-HALF_OPEN { color: var(--color-warn); }
    .layout { display: grid; grid-template-columns: 360px 1fr; gap: 1.25rem; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .painel { display: flex; flex-direction: column; gap: 1.25rem; }
    .field { display: flex; flex-direction: column; gap: .4rem; margin-bottom: 1rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .field input, .field textarea { width: 100%; }
    .botao-row { display: flex; gap: .5rem; align-items: center; margin-bottom: .5rem; }
    .botao-row input { flex: 1; min-width: 0; }
    .hint { display: block; margin-top: .6rem; font-size: .75rem; color: var(--text-color-secondary); }
    :host ::ng-deep .w-full { width: 100%; }
    .diag { margin-top: 1rem; }
    .diag-linha { display: flex; gap: .75rem; align-items: flex-start; font-size: .85rem; padding: .45rem 0; border-top: 1px solid var(--surface-border); }
    .diag-linha:first-child { border-top: none; }
    .diag-linha .k { color: var(--text-color-secondary); min-width: 96px; }
    .diag-linha .v { flex: 1; word-break: break-word; }
    .diag-linha .v i.ok { color: var(--color-success); }
    .diag-linha .v i.err { color: var(--color-danger); }
    .feed h3 { display: flex; align-items: center; gap: .5rem; margin: 0 0 .9rem; font-size: 1rem; }
    .mensagens { display: flex; flex-direction: column; gap: .65rem; max-height: 70vh; overflow-y: auto;
      padding: 1rem; background: var(--surface-ground); border: 1px solid var(--surface-border); border-radius: var(--content-border-radius); }
    .empty { color: var(--text-color-secondary); text-align: center; padding: 3rem 1rem; }
    .msg { max-width: 80%; padding: .55rem .8rem; border-radius: 10px; cursor: pointer;
      background: var(--surface-card); border: 1px solid var(--surface-border); }
    .msg.in { align-self: flex-start; border-top-left-radius: 3px; }
    .msg.out { align-self: flex-end; border-top-right-radius: 3px;
      background: color-mix(in srgb, var(--primary-color) 14%, var(--surface-card)); }
    .msg-meta { display: flex; gap: .5rem; align-items: center; font-size: .72rem; color: var(--text-color-secondary); margin-bottom: .25rem; }
    .msg-meta .tel { font-weight: 600; color: var(--text-color); }
    .msg-meta .tipo { background: var(--surface-ground); padding: .05rem .4rem; border-radius: 4px; font-size: .65rem; text-transform: uppercase; }
    .conteudo { font-size: .95rem; line-height: 1.35; white-space: pre-wrap; word-break: break-word; }
    .conteudo.vazio { color: var(--text-color-secondary); font-style: italic; }
    .time { font-size: .7rem; color: var(--text-color-secondary); text-align: right; margin-top: .2rem; }
  `],
})
export class TestesComponent implements OnInit {
  private api = inject(WhatsAppApiService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  readonly tipos = [
    { label: 'Texto', value: 'texto' },
    { label: 'Botões', value: 'botoes' },
  ];

  telefone = '';
  tipo: 'texto' | 'botoes' = 'texto';
  texto = 'Olá! Mensagem de teste do painel.';
  botoes: BotaoForm[] = [
    { id: 'confirmar', title: 'Confirmar' },
    { id: 'remarcar', title: 'Remarcar' },
  ];

  enviando = signal(false);
  diagnosticando = signal(false);
  diagnostico = signal<Diagnostico | null>(null);

  private feedOk = signal(false);
  private feedCarregou = signal(false);
  mensagens = signal<MonitorMensagem[]>([]);
  phoneNumberId = signal('');
  circuitBreaker = signal('UNKNOWN');
  total = signal(0);

  readonly feedAoVivo = computed(() => this.feedCarregou() && this.feedOk());

  ngOnInit(): void {
    this.buscarStatus();
    this.tick();
    // Polling ~2s do feed; takeUntilDestroyed encerra ao sair da tela.
    interval(2000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.tick());
  }

  private buscarStatus(): void {
    // Status geral vem do endpoint oficial /api/whatsapp/status (Bearer). Best-effort:
    // se falhar, o feed do /monitor ainda popula número/circuit.
    this.api.status().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (s) => {
        if (s.phoneNumberId) this.phoneNumberId.set(s.phoneNumberId);
        if (s.circuitBreakerState) this.circuitBreaker.set(s.circuitBreakerState);
      },
      error: () => { /* silencioso — o feed cobre esses campos */ },
    });
  }

  private tick(): void {
    this.api.feed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (f) => {
        this.feedOk.set(true);
        this.feedCarregou.set(true);
        this.phoneNumberId.set(f.phoneNumberId ?? this.phoneNumberId());
        this.circuitBreaker.set(f.circuitBreakerState ?? 'UNKNOWN');
        this.total.set(f.total ?? 0);
        this.mensagens.set(f.mensagens ?? []);
      },
      error: () => {
        this.feedOk.set(false);
        this.feedCarregou.set(true);
      },
    });
  }

  responder(m: MonitorMensagem): void {
    if (m.telefone) {
      this.telefone = m.telefone;
    }
  }

  adicionarBotao(): void {
    if (this.botoes.length < 3) {
      this.botoes = [...this.botoes, { id: '', title: '' }];
    }
  }

  removerBotao(i: number): void {
    if (this.botoes.length > 1) {
      this.botoes = this.botoes.filter((_, idx) => idx !== i);
    }
  }

  enviar(): void {
    const telefone = this.telefone.trim();
    if (!telefone) {
      this.msg.add({ severity: 'warn', summary: 'Enviar teste', detail: 'Informe o telefone.' });
      return;
    }
    const body: EnviarTeste = { telefone, tipo: this.tipo, texto: this.texto };
    if (this.tipo === 'botoes') {
      const botoes: Botao[] = this.botoes
        .map((b) => ({ id: b.id.trim(), title: b.title.trim() }))
        .filter((b) => b.id && b.title);
      if (!botoes.length) {
        this.msg.add({ severity: 'warn', summary: 'Enviar teste', detail: 'Informe ao menos um botão (id e título).' });
        return;
      }
      body.botoes = botoes;
    }

    this.enviando.set(true);
    this.api.enviarTeste(body).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (r) => {
        this.enviando.set(false);
        if (r.ok) {
          this.msg.add({ severity: 'success', summary: 'Enviado', detail: `wamid: ${r.wamid ?? '—'}` });
          this.tick();
        } else {
          const codigo = r.codigo ? `${r.codigo}` : 'Falha';
          const meta = r.metaErrorCode != null ? ` (Meta ${r.metaErrorCode})` : '';
          this.msg.add({ severity: 'error', summary: `${codigo}${meta}`, detail: r.mensagem ?? 'Não foi possível enviar.' });
        }
      },
      error: () => {
        this.enviando.set(false);
        this.msg.add({ severity: 'error', summary: 'Enviar teste', detail: 'Endpoint de envio indisponível (/monitor).' });
      },
    });
  }

  rodarDiagnostico(): void {
    this.diagnosticando.set(true);
    this.api.diagnostico().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (d) => {
        this.diagnosticando.set(false);
        this.diagnostico.set(d);
      },
      error: () => {
        this.diagnosticando.set(false);
        this.diagnostico.set(null);
        this.msg.add({ severity: 'error', summary: 'Diagnóstico', detail: 'Endpoint de diagnóstico indisponível (/monitor).' });
      },
    });
  }

  hora(ms: number | null): string {
    if (!ms) return '';
    return new Date(ms).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }
}
