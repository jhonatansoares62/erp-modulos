import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Injector, OnInit, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { Observable, finalize, interval, of, switchMap } from 'rxjs';
import { ConversaResumo, Mensagem, Page, WhatsAppApiService } from '../../core/whatsapp-api.service';

const POLL_LISTA_MS = 5000;
const POLL_CHAT_MS = 4000;
const PAGE_LISTA = 50;
const PAGE_MENSAGENS = 100;

/**
 * Inbox/Chat do atendente (rota `conversas`), estilo WhatsApp Web: lista de conversas
 * à esquerda, chat à direita. Somente texto (o backend expõe /enviar-texto).
 *
 * <p><b>Pollings (limpos via takeUntilDestroyed):</b>
 * <ul>
 *   <li>Lista: {@code GET /conversas} a cada 5s — atualiza sem perder a seleção
 *       (identificada por telefone) e re-sincroniza {@code janelaAberta} do selecionado.</li>
 *   <li>Chat aberto: {@code GET /conversas/{telefone}/mensagens} a cada 4s — só faz
 *       auto-scroll se o usuário já estava no fim (não "puxa" o scroll de quem rolou pra cima).</li>
 * </ul>
 *
 * <p><b>Janela 24h:</b> quando a conversa selecionada tem {@code janelaAberta === false},
 * o input e o botão de envio ficam desabilitados e um aviso explica o bloqueio da Meta.
 */
@Component({
  selector: 'app-conversas',
  standalone: true,
  imports: [FormsModule, ButtonModule, TagModule, TextareaModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="inbox">
      <!-- ── Coluna esquerda: lista de conversas ── -->
      <aside class="lista">
        <header class="lista-head">
          <span><i class="pi pi-comments"></i> Conversas</span>
          @if (conversas().length) {
            <span class="contador">{{ conversas().length }}</span>
          }
        </header>

        <div class="lista-corpo">
          @if (conversas().length) {
            @for (c of conversas(); track c.telefone) {
              <button type="button" class="item" [class.ativo]="c.telefone === selecionado()"
                      (click)="selecionar(c)">
                <div class="item-linha1">
                  <span class="titulo">{{ c.nome || c.telefone }}</span>
                  <span class="hora">{{ horaCurta(c.ultimaMensagemEm) }}</span>
                </div>
                <div class="item-linha2">
                  <i class="pi dir" [class.pi-arrow-up-right]="c.ultimaDirecao === 'SAIDA'"
                     [class.pi-arrow-down-left]="c.ultimaDirecao === 'ENTRADA'"
                     [class.saida]="c.ultimaDirecao === 'SAIDA'"
                     [class.entrada]="c.ultimaDirecao === 'ENTRADA'"
                     [title]="c.ultimaDirecao === 'SAIDA' ? 'Última: enviada' : 'Última: recebida'"></i>
                  <span class="preview" [class.vazio]="!c.ultimaMensagemPreview">
                    {{ c.ultimaMensagemPreview || 'Sem mensagens' }}
                  </span>
                  @if (!c.janelaAberta) {
                    <i class="pi pi-lock janela-lock" title="Janela de 24h fechada"></i>
                  }
                </div>
              </button>
            }
          } @else if (carregandoLista()) {
            <p class="lista-vazia">Carregando…</p>
          } @else {
            <p class="lista-vazia">Nenhuma conversa ainda</p>
          }
        </div>
      </aside>

      <!-- ── Coluna direita: chat ── -->
      <section class="chat">
        @if (selecionado(); as tel) {
          <header class="chat-head">
            <div class="quem">
              <i class="pi pi-user"></i>
              <div class="quem-txt">
                <strong>{{ selecionadoResumo()?.nome || tel }}</strong>
                <span>{{ tel }}</span>
              </div>
            </div>
            @if (janelaAberta()) {
              <p-tag value="Janela 24h aberta" severity="success" icon="pi pi-check-circle" />
            } @else {
              <p-tag value="Janela 24h fechada" severity="warn" icon="pi pi-lock" />
            }
          </header>

          @if (mensagens().length) {
            <div #painel class="mensagens" (scroll)="medirGrude()">
              @for (m of mensagens(); track m.id) {
                <div class="linha"
                     [class.saida]="m.direcao === 'SAIDA'" [class.entrada]="m.direcao === 'ENTRADA'">
                  <div class="bolha" [class.saida]="m.direcao === 'SAIDA'" [class.entrada]="m.direcao === 'ENTRADA'">
                    <div class="texto" [class.vazio]="!(m.preview || m.conteudo)">
                      {{ m.preview || m.conteudo || '(sem texto)' }}
                    </div>
                    <div class="rodape">
                      <span class="hora">{{ horaCurta(m.timestamp) }}</span>
                      @if (m.direcao === 'SAIDA' && m.status) {
                        <span class="status" [class]="'st-' + m.status">
                          <i class="pi" [class.pi-check]="m.status === 'sent'"
                             [class.pi-check-circle]="m.status === 'delivered'"
                             [class.pi-eye]="m.status === 'read'"
                             [class.pi-exclamation-circle]="m.status === 'failed'"></i>
                          {{ m.status }}
                        </span>
                      }
                    </div>
                  </div>
                </div>
              }
            </div>
          } @else if (carregandoChat()) {
            <div class="mensagens vazio"><p class="chat-vazio">Carregando mensagens…</p></div>
          } @else {
            <div class="mensagens vazio"><p class="chat-vazio">Nenhuma mensagem nesta conversa.</p></div>
          }

          <footer class="envio">
            @if (!janelaAberta()) {
              <div class="aviso-janela">
                <i class="pi pi-lock"></i>
                <span>Janela de 24h fechada — envio livre bloqueado pela Meta. Use um template.</span>
              </div>
            }
            <div class="barra">
              <textarea pTextarea [(ngModel)]="rascunho" rows="1" [autoResize]="true"
                        [disabled]="!janelaAberta() || enviando()"
                        (keydown.enter)="aoEnter($event)"
                        [placeholder]="janelaAberta() ? 'Escreva uma mensagem…' : 'Envio bloqueado (janela fechada)'"></textarea>
              <p-button icon="pi pi-send" [rounded]="true" [loading]="enviando()"
                        [disabled]="!janelaAberta() || !rascunho.trim()"
                        (onClick)="enviar()" ariaLabel="Enviar" />
            </div>
          </footer>
        } @else {
          <div class="chat-placeholder">
            <i class="pi pi-comments"></i>
            <p>Selecione uma conversa</p>
          </div>
        }
      </section>
    </div>
  `,
  styles: [`
    /* Quebra o padding/max-width do shell (.conteudo) para ocupar a área toda como um app de chat. */
    :host { display: block; margin: -1.75rem -1.5rem -3rem; }
    .inbox { display: grid; grid-template-columns: 340px 1fr; height: calc(100vh - 4rem - 3.05rem);
      background: var(--surface-ground); border-top: 1px solid var(--surface-border); }
    @media (max-width: 820px) { .inbox { grid-template-columns: 240px 1fr; } }

    /* Lista */
    .lista { display: flex; flex-direction: column; min-width: 0;
      background: var(--surface-card); border-right: 1px solid var(--surface-border); }
    .lista-head { display: flex; align-items: center; justify-content: space-between; gap: .5rem;
      padding: .9rem 1rem; font-weight: 600; border-bottom: 1px solid var(--surface-border); }
    .lista-head i { color: var(--primary-color); margin-right: .4rem; }
    .lista-head .contador { font-size: .75rem; font-weight: 600; color: var(--text-color-secondary);
      background: var(--surface-ground); border-radius: 999px; padding: .1rem .55rem; }
    .lista-corpo { flex: 1; overflow-y: auto; }
    .lista-vazia { color: var(--text-color-secondary); text-align: center; padding: 2.5rem 1rem; font-size: .9rem; }
    .item { display: block; width: 100%; text-align: left; background: none; border: none; cursor: pointer;
      padding: .7rem 1rem; border-bottom: 1px solid var(--surface-border); color: var(--text-color);
      transition: background .12s; }
    .item:hover { background: var(--surface-ground); }
    .item.ativo { background: color-mix(in srgb, var(--primary-color) 12%, var(--surface-card)); }
    .item-linha1 { display: flex; align-items: baseline; justify-content: space-between; gap: .5rem; }
    .item-linha1 .titulo { font-weight: 600; font-size: .92rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .item-linha1 .hora { font-size: .72rem; color: var(--text-color-secondary); flex: none; }
    .item-linha2 { display: flex; align-items: center; gap: .4rem; margin-top: .2rem; }
    .item-linha2 .dir { font-size: .7rem; flex: none; }
    .item-linha2 .dir.saida { color: var(--primary-color); }
    .item-linha2 .dir.entrada { color: var(--text-color-secondary); }
    .item-linha2 .preview { flex: 1; min-width: 0; font-size: .82rem; color: var(--text-color-secondary);
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .item-linha2 .preview.vazio { font-style: italic; opacity: .7; }
    .item-linha2 .janela-lock { font-size: .72rem; color: var(--color-warn, #d97706); flex: none; }

    /* Chat */
    .chat { display: flex; flex-direction: column; min-width: 0; }
    .chat-head { display: flex; align-items: center; justify-content: space-between; gap: 1rem;
      padding: .75rem 1.25rem; background: var(--surface-card); border-bottom: 1px solid var(--surface-border); }
    .chat-head .quem { display: flex; align-items: center; gap: .7rem; min-width: 0; }
    .chat-head .quem i { font-size: 1.1rem; color: var(--primary-color);
      background: var(--surface-ground); border-radius: 50%; padding: .5rem; }
    .chat-head .quem-txt { display: flex; flex-direction: column; line-height: 1.2; min-width: 0; }
    .chat-head .quem-txt strong { font-size: .95rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .chat-head .quem-txt span { font-size: .75rem; color: var(--text-color-secondary); }

    /* Painel do chat: coluna rolável. gap separa as bolhas; cada .linha alinha a bolha
       à esquerda (entrada) ou à direita (saída). */
    .mensagens { flex: 1; min-height: 0; overflow-y: auto; padding: 1.25rem;
      display: flex; flex-direction: column; gap: .5rem; }
    /* .linha = faixa 100% da largura que empurra a bolha p/ esquerda (entrada) ou direita (saída). */
    .linha { display: flex; padding-block: .25rem; }
    .linha.entrada { justify-content: flex-start; }
    .linha.saida { justify-content: flex-end; }
    .mensagens.vazio { align-items: center; justify-content: center; }
    .chat-vazio { margin: auto; color: var(--text-color-secondary); font-size: .9rem; }
    .bolha { max-width: 68%; padding: .5rem .75rem; border-radius: 12px; box-shadow: 0 1px 1px rgba(0,0,0,.06); }
    .bolha.entrada { background: var(--surface-card);
      border: 1px solid var(--surface-border); border-top-left-radius: 3px; }
    .bolha.saida { border-top-right-radius: 3px;
      background: var(--p-primary-100, #d1fae5); border: 1px solid var(--p-primary-200, #a7f3d0); }
    .bolha .texto { font-size: .92rem; line-height: 1.4; white-space: pre-wrap; word-break: break-word; }
    .bolha .texto.vazio { font-style: italic; color: var(--text-color-secondary); }
    .bolha .rodape { display: flex; align-items: center; justify-content: flex-end; gap: .4rem; margin-top: .2rem; }
    .bolha .rodape .hora { font-size: .68rem; color: var(--text-color-secondary); }
    .bolha .rodape .status { font-size: .66rem; display: inline-flex; align-items: center; gap: .2rem; text-transform: capitalize; }
    .bolha .rodape .status i { font-size: .7rem; }
    .bolha .rodape .st-read { color: var(--color-success, #059669); }
    .bolha .rodape .st-delivered { color: var(--text-color-secondary); }
    .bolha .rodape .st-sent { color: var(--text-color-secondary); }
    .bolha .rodape .st-failed { color: var(--color-danger, #dc2626); }

    /* Envio */
    .envio { border-top: 1px solid var(--surface-border); background: var(--surface-card); padding: .75rem 1rem; }
    .aviso-janela { display: flex; align-items: center; gap: .5rem; font-size: .8rem;
      color: var(--color-warn, #b45309); background: color-mix(in srgb, var(--color-warn, #f59e0b) 12%, var(--surface-card));
      border: 1px solid color-mix(in srgb, var(--color-warn, #f59e0b) 35%, transparent);
      border-radius: 8px; padding: .5rem .75rem; margin-bottom: .6rem; }
    .barra { display: flex; align-items: flex-end; gap: .6rem; }
    /* Textarea de 1 linha casa a altura do p-button rounded (2.75rem/44px); com autoResize
       cresce e, por align-items:flex-end, o botão fica ancorado embaixo. */
    .barra textarea { flex: 1; resize: none; min-height: 2.75rem; max-height: 8rem;
      padding-block: .55rem; line-height: 1.5; border-radius: 1.35rem; }
    .barra p-button { flex: none; }

    .chat-placeholder { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: .6rem; color: var(--text-color-secondary); }
    .chat-placeholder i { font-size: 2.75rem; color: var(--primary-color); opacity: .55; }
    .chat-placeholder p { font-size: 1rem; }
  `],
})
export class ConversasComponent implements OnInit {
  private api = inject(WhatsAppApiService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);
  private injector = inject(Injector);

  // Div rolável do chat (#painel). Vive dentro de um @if — o signal fica undefined
  // enquanto não há mensagens e volta a resolver quando o painel reaparece.
  private readonly painel = viewChild<ElementRef<HTMLElement>>('painel');

  conversas = signal<ConversaResumo[]>([]);
  carregandoLista = signal(false);

  selecionado = signal<string | null>(null);
  mensagens = signal<Mensagem[]>([]);
  carregandoChat = signal(false);
  enviando = signal(false);

  rascunho = '';

  // true = usuário está (ou queremos manter) no fim do histórico → auto-scroll ao chegar msg nova.
  private grudadoNoFim = true;

  readonly selecionadoResumo = computed(() =>
    this.conversas().find((c) => c.telefone === this.selecionado()) ?? null,
  );

  // Fonte da verdade da trava 24h: janelaAberta do resumo da conversa selecionada.
  readonly janelaAberta = computed(() => this.selecionadoResumo()?.janelaAberta ?? false);

  ngOnInit(): void {
    this.carregarLista(true);
    interval(POLL_LISTA_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.carregarLista(false));
    interval(POLL_CHAT_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.selecionado()) {
        this.carregarMensagens(false);
      }
    });
  }

  selecionar(c: ConversaResumo): void {
    if (c.telefone === this.selecionado()) {
      return;
    }
    this.selecionado.set(c.telefone);
    this.mensagens.set([]);
    this.rascunho = '';
    this.grudadoNoFim = true;
    this.carregarMensagens(true);
  }

  private carregarLista(comLoading: boolean): void {
    if (comLoading) {
      this.carregandoLista.set(true);
    }
    this.api.listarConversas(0, PAGE_LISTA).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => { if (comLoading) this.carregandoLista.set(false); }),
    ).subscribe({
      next: (pg) => this.conversas.set(pg.content ?? []),
      error: () => {
        if (comLoading) {
          this.msg.add({ severity: 'error', summary: 'Conversas', detail: 'Falha ao carregar a lista de conversas.' });
        }
        // Em polling silencioso, não incomoda com toast — mantém a lista atual.
      },
    });
  }

  private carregarMensagens(comLoading: boolean): void {
    const tel = this.selecionado();
    if (!tel) {
      return;
    }
    if (comLoading) {
      this.carregandoChat.set(true);
    }
    this.buscarUltimaPagina(tel).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => { if (comLoading) this.carregandoChat.set(false); }),
    ).subscribe({
      next: (pg) => {
        if (tel !== this.selecionado()) {
          return; // trocou de conversa durante a requisição — descarta resposta obsoleta
        }
        const novas = pg.content ?? [];
        const anterior = this.mensagens();
        const cresceu = novas.length > anterior.length
          || (novas.length > 0 && anterior.length > 0 && novas[novas.length - 1].id !== anterior[anterior.length - 1].id);
        this.mensagens.set(novas);
        // Rola pro fim ao abrir (comLoading) ou quando chegou algo novo e o usuário estava no fim.
        if (comLoading || (cresceu && this.grudadoNoFim)) {
          this.rolarParaFim();
        }
      },
      error: () => {
        if (comLoading) {
          this.msg.add({ severity: 'error', summary: 'Conversa', detail: 'Falha ao carregar as mensagens.' });
        }
      },
    });
  }

  /**
   * Busca a ÚLTIMA página de mensagens (as mais recentes). A API pagina em ordem
   * cronológica ASC e ignora sort: a página 0 traz as mais ANTIGAS. Então pedimos a
   * page 0 só pra ler totalPages e, havendo mais de uma, refazemos na última página —
   * assim o fim do scroll é a mensagem mais recente real. (Carregar mais antigas ao
   * rolar pra cima fica pra depois.)
   */
  private buscarUltimaPagina(tel: string): Observable<Page<Mensagem>> {
    return this.api.listarMensagens(tel, 0, PAGE_MENSAGENS).pipe(
      switchMap((pg) =>
        pg.totalPages > 1
          ? this.api.listarMensagens(tel, pg.totalPages - 1, PAGE_MENSAGENS)
          : of(pg),
      ),
    );
  }

  medirGrude(): void {
    const el = this.painel()?.nativeElement;
    if (!el) {
      return;
    }
    // "Grudado" se está a menos de ~60px do fim — auto-rola só quem já estava no fim.
    this.grudadoNoFim = (el.scrollHeight - el.scrollTop - el.clientHeight) < 60;
  }

  aoEnter(ev: Event): void {
    const ke = ev as KeyboardEvent;
    if (ke.shiftKey) {
      return; // Shift+Enter = quebra de linha
    }
    ev.preventDefault();
    this.enviar();
  }

  enviar(): void {
    const tel = this.selecionado();
    const texto = this.rascunho.trim();
    if (!tel || !texto || !this.janelaAberta() || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.api.enviarTexto(tel, texto).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.enviando.set(false)),
    ).subscribe({
      next: () => {
        this.rascunho = '';
        this.grudadoNoFim = true;
        this.carregarMensagens(false); // força refresh do histórico (a SAÍDA já foi logada no backend)
        this.carregarLista(false); // atualiza preview/ordem da lista
      },
      error: (err) => {
        const detalhe = err?.status === 409
          ? 'Janela de 24h fechada — a Meta recusou o envio livre.'
          : (err?.error?.mensagem || err?.error?.message || 'Não foi possível enviar a mensagem.');
        this.msg.add({ severity: 'error', summary: 'Envio', detail: detalhe });
      },
    });
  }

  horaCurta(iso: string | null): string {
    if (!iso) {
      return '';
    }
    const d = new Date(iso);
    if (isNaN(d.getTime())) {
      return '';
    }
    const agora = new Date();
    const mesmoDia = d.toDateString() === agora.toDateString();
    if (mesmoDia) {
      return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    }
    return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  }

  private rolarParaFim(): void {
    this.grudadoNoFim = true;
    // Ancora no fim após o próximo render (o @for já refletiu mensagens()). Um único ajuste
    // extra via rAF cobre reflow tardio (autoResize do textarea, carregamento de fontes).
    afterNextRender(() => {
      const el = this.painel()?.nativeElement;
      if (!el) {
        return;
      }
      el.scrollTop = el.scrollHeight;
      requestAnimationFrame(() => {
        const alvo = this.painel()?.nativeElement;
        if (alvo) {
          alvo.scrollTop = alvo.scrollHeight;
        }
      });
    }, { injector: this.injector });
  }
}
