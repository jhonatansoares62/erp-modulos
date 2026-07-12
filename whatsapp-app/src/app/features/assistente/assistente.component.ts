import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { finalize } from 'rxjs';
import { AssistenteConfig, AssistenteUpdate, WhatsAppApiService } from '../../core/whatsapp-api.service';

/**
 * Persona do assistente virtual (GET/PUT /api/whatsapp/assistente). Config GENÉRICA,
 * reusável entre ERPs: identidade (nome/emoji/tom), saudação, mensagens de canal
 * ("não entendi" / "fora do horário") e horário de atendimento. Sem secrets — todos
 * os campos voltam em claro. O ERP consome a persona pelo bloco "assistente" do
 * callback e renderiza as mensagens de domínio (menu, agendamento) com ela.
 */
@Component({
  selector: 'app-assistente',
  standalone: true,
  imports: [FormsModule, CardModule, InputTextModule, TextareaModule, SelectButtonModule, ButtonModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-card>
      <ng-template #title>
        <div class="titulo-linha">
          <span><i class="pi pi-user-edit"></i> Assistente Virtual</span>
          @if (config(); as c) {
            <p-tag [value]="c.nome || 'sem nome'" severity="info" icon="pi pi-comment" />
          }
        </div>
      </ng-template>

      @if (config(); as c) {
        <p class="ajuda">
          Personalize a identidade do assistente e as mensagens automáticas. Estes textos são
          <strong>genéricos</strong> (identidade e canal); as mensagens específicas da clínica
          (menu, agendamento) são montadas pelo ERP usando esta persona.
          @if (c.atualizadoEm) { <br />Última atualização: {{ fmtData(c.atualizadoEm) }}. }
        </p>

        <form class="form" (ngSubmit)="salvar()">
          <div class="linha">
            <div class="field grow">
              <label for="nome">Nome do assistente</label>
              <input pInputText id="nome" name="nome" [(ngModel)]="nome" placeholder="Ex.: Sofia" autocomplete="off" />
            </div>
            <div class="field emoji">
              <label for="emoji">Emoji</label>
              <input pInputText id="emoji" name="emoji" [(ngModel)]="emoji" placeholder="🦷" autocomplete="off" />
            </div>
          </div>

          <div class="field">
            <label>Tom de tratamento</label>
            <p-selectButton [options]="tomOpcoes" [(ngModel)]="tom" name="tom"
                            optionLabel="label" optionValue="value" [allowEmpty]="false" />
          </div>

          <div class="field">
            <label for="saudacao">Saudação</label>
            <textarea pTextarea id="saudacao" name="saudacao" [(ngModel)]="saudacao" rows="2" [autoResize]="true"
                      placeholder="Olá! Como posso ajudar você hoje?"></textarea>
            <small>Variáveis que o ERP pode usar: &#123;nome&#125;, &#123;assistente&#125;, &#123;empresa&#125;.</small>
          </div>

          <div class="field">
            <label for="naoEntendi">Mensagem "não entendi"</label>
            <textarea pTextarea id="naoEntendi" name="naoEntendi" [(ngModel)]="mensagemNaoEntendi" rows="2" [autoResize]="true"
                      placeholder="Desculpe, não entendi. Vou te mostrar as opções."></textarea>
          </div>

          <div class="field">
            <label for="foraHorario">Mensagem "fora do horário"</label>
            <textarea pTextarea id="foraHorario" name="foraHorario" [(ngModel)]="mensagemForaHorario" rows="2" [autoResize]="true"
                      placeholder="No momento estamos fora do horário de atendimento."></textarea>
          </div>

          <fieldset class="horario">
            <legend>Horário de atendimento</legend>
            <div class="linha">
              <div class="field">
                <label for="hi">Início</label>
                <input pInputText id="hi" name="hi" [(ngModel)]="horarioInicio" placeholder="08:00" autocomplete="off" />
              </div>
              <div class="field">
                <label for="hf">Fim</label>
                <input pInputText id="hf" name="hf" [(ngModel)]="horarioFim" placeholder="18:00" autocomplete="off" />
              </div>
              <div class="field grow">
                <label for="dias">Dias</label>
                <input pInputText id="dias" name="dias" [(ngModel)]="diasAtendimento" placeholder="1,2,3,4,5" autocomplete="off" />
                <small>1=Seg, 2=Ter … 7=Dom (separados por vírgula).</small>
              </div>
            </div>
          </fieldset>

          <div class="acoes">
            <p-button type="submit" label="Salvar" icon="pi pi-save" [loading]="salvando()" />
            <p-button type="button" label="Recarregar" icon="pi pi-refresh" severity="secondary" [text]="true"
                      [loading]="loading()" (onClick)="carregar()" />
          </div>
        </form>
      } @else if (loading()) {
        <p class="ajuda">Carregando…</p>
      } @else {
        <p class="ajuda">Não foi possível carregar a persona.
          <p-button label="Tentar de novo" icon="pi pi-refresh" [text]="true" (onClick)="carregar()" />
        </p>
      }
    </p-card>
  `,
  styles: [`
    :host { display: block; }
    .titulo-linha { display: flex; align-items: center; justify-content: space-between; gap: 1rem; flex-wrap: wrap; }
    .titulo-linha > span { display: inline-flex; align-items: center; gap: .5rem; }
    .ajuda { color: var(--text-color-secondary); font-size: .9rem; margin-bottom: 1.25rem; line-height: 1.5; }
    .form { display: flex; flex-direction: column; gap: 1.1rem; max-width: 40rem; }
    .linha { display: flex; gap: 1rem; flex-wrap: wrap; }
    .field { display: flex; flex-direction: column; gap: .4rem; }
    .field.grow { flex: 1; min-width: 12rem; }
    .field.emoji { width: 6rem; }
    .field label { font-size: .85rem; font-weight: 500; }
    .field small { color: var(--text-color-secondary); font-size: .78rem; }
    .field input, .field textarea { width: 100%; }
    .horario { border: 1px solid var(--surface-border); border-radius: 8px; padding: 1rem 1rem .25rem; margin: 0; }
    .horario legend { font-size: .82rem; font-weight: 600; padding: 0 .4rem; color: var(--text-color-secondary); }
    .acoes { display: flex; gap: .75rem; align-items: center; margin-top: .25rem; }
  `],
})
export class AssistenteComponent implements OnInit {
  private api = inject(WhatsAppApiService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  loading = signal(false);
  salvando = signal(false);
  config = signal<AssistenteConfig | null>(null);

  readonly tomOpcoes = [
    { label: 'Informal (você)', value: 'informal' },
    { label: 'Formal (senhor/senhora)', value: 'formal' },
  ];

  nome = '';
  emoji = '';
  tom = 'informal';
  saudacao = '';
  mensagemNaoEntendi = '';
  mensagemForaHorario = '';
  horarioInicio = '';
  horarioFim = '';
  diasAtendimento = '';

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.loading.set(true);
    this.api.obterAssistente().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false))).subscribe({
      next: (c) => { this.config.set(c); this.preencher(c); },
      error: () => {
        this.config.set(null);
        this.msg.add({ severity: 'error', summary: 'Assistente', detail: 'Falha ao carregar a persona do assistente.' });
      },
    });
  }

  private preencher(c: AssistenteConfig): void {
    this.nome = c.nome ?? '';
    this.emoji = c.emoji ?? '';
    this.tom = c.tom || 'informal';
    this.saudacao = c.saudacao ?? '';
    this.mensagemNaoEntendi = c.mensagemNaoEntendi ?? '';
    this.mensagemForaHorario = c.mensagemForaHorario ?? '';
    this.horarioInicio = c.horarioInicio ?? '';
    this.horarioFim = c.horarioFim ?? '';
    this.diasAtendimento = c.diasAtendimento ?? '';
  }

  salvar(): void {
    if (!this.nome.trim()) {
      this.msg.add({ severity: 'warn', summary: 'Assistente', detail: 'O nome do assistente é obrigatório.' });
      return;
    }
    const body: AssistenteUpdate = {
      nome: this.nome.trim(),
      emoji: this.emoji.trim(),
      tom: this.tom,
      saudacao: this.saudacao.trim(),
      mensagemNaoEntendi: this.mensagemNaoEntendi.trim(),
      mensagemForaHorario: this.mensagemForaHorario.trim(),
      horarioInicio: this.horarioInicio.trim(),
      horarioFim: this.horarioFim.trim(),
      diasAtendimento: this.diasAtendimento.trim(),
    };
    this.salvando.set(true);
    this.api.salvarAssistente(body).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.salvando.set(false))).subscribe({
      next: (c) => {
        this.config.set(c);
        this.preencher(c);
        this.msg.add({ severity: 'success', summary: 'Assistente', detail: 'Persona salva.' });
      },
      error: (err) => this.msg.add({
        severity: 'error',
        summary: 'Assistente',
        detail: err?.status === 400 ? 'Dados inválidos — confira os campos.' : 'Falha ao salvar a persona.',
      }),
    });
  }

  fmtData(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? iso : d.toLocaleString('pt-BR');
  }
}
