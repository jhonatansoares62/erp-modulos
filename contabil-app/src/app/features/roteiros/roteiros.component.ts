import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService } from 'primeng/api';
import { finalize } from 'rxjs';
import { PRIMENG_MODULES } from '../../shared/primeng';
import { BotaoComponent } from '../../shared/components/botao/botao.component';
import { BotaoAcaoComponent } from '../../shared/components/botao/botao-acao.component';
import { Conta, ContabilidadeService, Roteiro, RoteiroCreate } from '../../shared/services/contabilidade.service';
import { CATALOGO_EVENTOS, CatalogoEvento } from './catalogo-eventos';
import { CardStat } from '../../shared/models';

interface LinhaPartida {
  tipo: 'D' | 'C';
  contaCodigo: string;
}

interface GrupoRoteiros {
  tipo: string;
  rotulo: string;
  icone: string;
  itens: Roteiro[];
}

interface TabelaRow {
  roteiro: Roteiro;
  evRotulo: string;
  evIcone: string;
}

interface Partida {
  nome: string;
  codigo?: string;
}

type Visualizacao = 'cards' | 'lista' | 'tabela';

const EVENTOS_CONHECIDOS: { tipo: string; rotulo: string; icone: string }[] = [
  { tipo: 'venda.finalizada',    rotulo: 'Venda finalizada',    icone: 'pi pi-shopping-cart' },
  { tipo: 'recebimento.baixado', rotulo: 'Recebimento',         icone: 'pi pi-wallet' },
  { tipo: 'compra.recebida',     rotulo: 'Compra recebida',     icone: 'pi pi-box' },
  { tipo: 'pagamento.efetuado',  rotulo: 'Pagamento efetuado',  icone: 'pi pi-arrow-up' },
  { tipo: 'despesa.incorrida',   rotulo: 'Despesa incorrida',   icone: 'pi pi-receipt' },
];
const VALOR_ROTULO: Record<string, string> = {
  avista: 'à vista', prazo: 'a prazo', pix: 'PIX', dinheiro: 'Dinheiro', cartao: 'Cartão',
  credito: 'Cartão de crédito', debito: 'Cartão de débito', boleto: 'Boleto',
  insumo: 'Insumo', produto: 'Produto', servico: 'Serviço',
};

/**
 * Gestão de roteiros (regras evento → partidas). Lista/cria/desativa as regras que traduzem
 * eventos de negócio em lançamentos. Cards totalizadores no topo, busca e três visualizações
 * (Cards, Lista agrupada por evento e Tabela). Condições legíveis (chips) e partidas por conta.
 */
@Component({
  selector: 'app-config-roteiros-tab',
  standalone: true,
  imports: [FormsModule, ...PRIMENG_MODULES, BotaoComponent, BotaoAcaoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rot-head">
      <p class="rot-info">
        Roteiros que traduzem eventos de negócio em lançamentos. Crie aqui ou use "salvar como regra"
        ao classificar uma pendência.
      </p>
      <div class="rot-acoes">
        <app-botao label="Novo roteiro" icon="pi pi-plus" size="small" (clicado)="abrirNovo()" />
        <app-botao label="Atualizar" icon="pi pi-refresh" [text]="true" size="small" (clicado)="carregar()" />
      </div>
    </div>

    <!-- Cards totalizadores -->
    @if (!loading() && roteiros().length > 0) {
      <div class="cards-grid">
        @for (card of cards(); track card.label) {
          <p-card>
            <div class="card-stat">
              <div class="card-stat-icon" [style.background]="card.bg">
                <i [class]="card.icon" [style.color]="card.color"></i>
              </div>
              <div class="card-stat-info">
                <span class="card-stat-value">{{ card.value }}</span>
                <span class="card-stat-label">{{ card.label }}</span>
              </div>
            </div>
          </p-card>
        }
      </div>
    }

    @if (loading()) {
      <p>Carregando roteiros...</p>
    } @else if (roteiros().length === 0) {
      <p class="vazio">Nenhum roteiro cadastrado.</p>
    } @else {
      <!-- Toolbar: busca + toggle -->
      <div class="rot-toolbar">
        <div class="busca-wrap">
          <i class="pi pi-search"></i>
          <input pInputText [ngModel]="filtro()" (ngModelChange)="filtro.set($event)"
                 placeholder="Buscar por evento, condição ou conta" class="w-full" />
        </div>
        <p-selectButton [ngModel]="visualizacao()" (ngModelChange)="visualizacao.set($event)"
                        [options]="opcoesVis" optionLabel="label" optionValue="value"
                        [allowEmpty]="false" styleClass="vis-toggle" />
      </div>

      @if (grupos().length === 0) {
        <p class="vazio">Nenhum roteiro corresponde à busca.</p>
      }

      <!-- ── CARDS ── -->
      @if (visualizacao() === 'cards') {
        @for (g of grupos(); track g.tipo) {
          <section class="rot-sec">
            <header class="rot-sec-head">
              <i [class]="g.icone" class="rot-sec-ico"></i>
              <span class="rot-sec-nome">{{ g.rotulo }}</span>
              <code class="rot-sec-slug">{{ g.tipo }}</code>
              <span class="rot-sec-count">{{ g.itens.length }} {{ g.itens.length === 1 ? 'roteiro' : 'roteiros' }}</span>
            </header>
            <div class="rot-cards-grid">
              @for (r of g.itens; track r.id) {
                <div class="rot-card">
                  <div class="rot-card-head">
                    @if (chips(r).length) {
                      <span class="rot-chips">
                        @for (c of chips(r); track c) { <p-tag [value]="c" severity="secondary" styleClass="rot-chip" /> }
                      </span>
                    } @else {
                      <span class="rot-nocond">vale sempre</span>
                    }
                    <span class="rot-prio-badge">P{{ r.prioridade }}</span>
                  </div>
                  <div class="rot-card-parts">
                    @for (p of parts(r, 'D'); track $index) {
                      <div class="rot-part-line"><span class="rot-dc rot-dc--d">D</span> <b>{{ p.nome }}</b> <span class="rot-code">{{ p.codigo }}</span></div>
                    }
                    @for (p of parts(r, 'C'); track $index) {
                      <div class="rot-part-line"><span class="rot-dc rot-dc--c">C</span> <b>{{ p.nome }}</b> <span class="rot-code">{{ p.codigo }}</span></div>
                    }
                  </div>
                  <div class="rot-card-foot">
                    <app-botao-acao acao="custom" icon="pi pi-ban" severity="danger"
                                    tooltip="Desativar roteiro" (clicado)="confirmarDesativar(r)" />
                  </div>
                </div>
              }
            </div>
          </section>
        }
      }

      <!-- ── LISTA (agrupada por evento) ── -->
      @if (visualizacao() === 'lista') {
        <div class="rot-lista">
          @for (g of grupos(); track g.tipo) {
            <div class="grp-head">
              <i [class]="g.icone" class="grp-ico"></i>
              <span class="grp-nome">{{ g.rotulo }}</span>
              <code class="grp-slug">{{ g.tipo }}</code>
              <span class="grp-count">{{ g.itens.length }} {{ g.itens.length === 1 ? 'roteiro' : 'roteiros' }}</span>
            </div>
            @for (r of g.itens; track r.id) {
              <div class="rot-row">
                <div class="rot-top">
                  @if (chips(r).length) {
                    <span class="rot-chips">
                      @for (c of chips(r); track c) { <p-tag [value]="c" severity="secondary" styleClass="rot-chip" /> }
                    </span>
                  } @else {
                    <span class="rot-nocond">sem condição — vale sempre</span>
                  }
                  <span class="rot-right">
                    @if (g.itens.length > 1) { <span class="rot-prio">prioridade {{ r.prioridade }}</span> }
                    <app-botao-acao acao="custom" icon="pi pi-ban" severity="danger"
                                    tooltip="Desativar roteiro" (clicado)="confirmarDesativar(r)" />
                  </span>
                </div>
                <div class="rot-parts">
                  @for (p of r.partidas; track $index) {
                    <span class="rot-part">
                      <span class="rot-dc" [class.rot-dc--d]="p.tipo === 'D'" [class.rot-dc--c]="p.tipo === 'C'">{{ p.tipo }}</span>
                      <b>{{ p.contaNome || p.contaCampo || '?' }}</b>
                      <span class="rot-code">{{ p.contaCodigo }}</span>
                    </span>
                  }
                </div>
              </div>
            }
          }
        </div>
      }

      <!-- ── TABELA ── -->
      @if (visualizacao() === 'tabela') {
        <div class="rot-card-wrap">
          <p-table [value]="tabela()" dataKey="roteiro.id" [tableStyle]="{'min-width': '55rem'}" styleClass="p-datatable-sm">
            <ng-template #header>
              <tr>
                <th style="width:180px">Evento</th>
                <th style="width:180px">Condição</th>
                <th>Débito</th>
                <th>Crédito</th>
                <th style="width:90px">Prioridade</th>
                <th style="width:70px">Ações</th>
              </tr>
            </ng-template>
            <ng-template #body let-row>
              <tr>
                <td><span class="tab-ev"><i [class]="row.evIcone"></i> {{ row.evRotulo }}</span></td>
                <td>
                  @if (chips(row.roteiro).length) {
                    <span class="rot-chips">
                      @for (c of chips(row.roteiro); track c) { <p-tag [value]="c" severity="secondary" styleClass="rot-chip" /> }
                    </span>
                  } @else { <span class="rot-nocond">—</span> }
                </td>
                <td>
                  @for (p of parts(row.roteiro, 'D'); track $index) {
                    <div class="tab-part"><b>{{ p.nome }}</b> <span class="rot-code">{{ p.codigo }}</span></div>
                  }
                </td>
                <td>
                  @for (p of parts(row.roteiro, 'C'); track $index) {
                    <div class="tab-part"><b>{{ p.nome }}</b> <span class="rot-code">{{ p.codigo }}</span></div>
                  }
                </td>
                <td>{{ row.roteiro.prioridade }}</td>
                <td>
                  <app-botao-acao acao="custom" icon="pi pi-ban" severity="danger"
                                  tooltip="Desativar roteiro" (clicado)="confirmarDesativar(row.roteiro)" />
                </td>
              </tr>
            </ng-template>
          </p-table>
        </div>
      }
    }

    <p-dialog [visible]="dialogVisivel()" (visibleChange)="dialogVisivel.set($event)"
              [modal]="true" header="Novo roteiro" [style]="{ width: '40rem' }">
      <div class="dlg-form">
        <div class="field field--full">
          <label>Evento</label>
          <p-select [ngModel]="eventoSel()" (ngModelChange)="selecionarEvento($event)"
                    [options]="catalogo" optionLabel="rotulo"
                    placeholder="Escolha o evento de negócio" appendTo="body" styleClass="w-full" />
          @if (eventoSel()) { <small class="dlg-hint">{{ eventoSel()!.descricao }}</small> }
        </div>

        @if (eventoSel()) {
          @for (d of eventoSel()!.dimensoes; track d.chave) {
            <div class="field field--full">
              <label>{{ d.rotulo }}</label>
              <p-select [ngModel]="condValores()[d.chave] || ''"
                        (ngModelChange)="setCond(d.chave, $event)"
                        [options]="opcoesDim()[d.cadastroRef] || []" optionLabel="label" optionValue="value"
                        appendTo="body" styleClass="w-full" />
            </div>
          }
        }

        <div class="field field--full">
          <label>Histórico (opcional)</label>
          <input pInputText [(ngModel)]="historico" placeholder="Ex.: Venda {numero}" class="w-full" />
        </div>
      </div>

      <div class="part-head">
        <label>Partidas</label>
        <app-botao label="+ partida" [text]="true" size="small" (clicado)="addLinha()" />
      </div>
      @for (l of linhas(); track $index) {
        <div class="part-linha">
          <p-select [(ngModel)]="l.tipo" [options]="tipos" optionLabel="label" optionValue="value"
                    appendTo="body" styleClass="part-tipo" />
          <p-select [(ngModel)]="l.contaCodigo" [options]="contasOpcoes()" optionLabel="label" optionValue="value"
                    [filter]="true" placeholder="Conta" appendTo="body" styleClass="part-conta" />
          <app-botao icon="pi pi-times" [text]="true" severity="secondary" size="small"
                     [disabled]="linhas().length <= 2" (clicado)="removerLinha($index)" />
        </div>
      }
      @if (previa()) { <p class="dlg-previa">{{ previa() }}</p> }
      <p class="dlg-dica">O lançamento usa o valor total do evento. Precisa de ao menos um débito e um crédito.</p>

      <ng-template #footer>
        <app-botao label="Cancelar" [text]="true" severity="secondary" (clicado)="dialogVisivel.set(false)" />
        <app-botao label="Criar roteiro" icon="pi pi-check" [loading]="salvando()"
                   [disabled]="!podeSalvar()" (clicado)="salvar()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    :host { display: block; }
    .rot-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; margin-bottom: 1rem; }
    .rot-acoes { display: flex; align-items: center; gap: .5rem; flex-shrink: 0; }
    .rot-info { margin: 0; font-size: .85rem; color: var(--text-color-secondary); }
    .vazio { color: var(--text-color-secondary); }

    /* Cards totalizadores — padrão do Financeiro/Plano de Contas */
    .cards-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .card-stat { display: flex; align-items: center; gap: 1rem; padding: .25rem 0; }
    .card-stat-icon { width: 50px; height: 50px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .card-stat-icon i { font-size: 1.4rem; }
    .card-stat-info { display: flex; flex-direction: column; }
    .card-stat-value { font-family: var(--font-display, inherit); font-size: 1.8rem; font-weight: 800; letter-spacing: var(--tracking-tight, -.02em); }
    .card-stat-label { font-family: var(--font-body, inherit); font-size: .75rem; color: var(--p-text-muted-color, var(--text-color-secondary));
      text-transform: uppercase; letter-spacing: var(--tracking-wide, .04em); font-weight: 500; }

    /* Toolbar */
    .rot-toolbar { display: flex; align-items: center; gap: 1rem; margin-bottom: 1.25rem; flex-wrap: wrap; }
    .busca-wrap { position: relative; flex: 1; min-width: 220px; }
    .busca-wrap i { position: absolute; left: .75rem; top: 50%; transform: translateY(-50%); color: var(--text-color-secondary); font-size: .9rem; pointer-events: none; }
    .busca-wrap input { padding-left: 2.25rem; }
    :host ::ng-deep .vis-toggle { flex-shrink: 0; }

    /* Chips / partidas compartilhados */
    .rot-chips { display: inline-flex; gap: .35rem; flex-wrap: wrap; }
    :host ::ng-deep .rot-chip { font-size: .72rem; }
    .rot-nocond { font-size: .78rem; color: var(--text-color-secondary); font-style: italic; }
    .rot-code { font-size: .7rem; color: var(--text-color-secondary); margin-left: .3rem; font-family: var(--font-family-mono, monospace); }
    .rot-dc { display: inline-block; min-width: 15px; text-align: center; font-size: .7rem; font-weight: 700;
      padding: 0 .25rem; border-radius: 4px; background: var(--surface-200); color: var(--text-color-secondary); }
    .rot-dc--d { background: color-mix(in srgb, #2D7D5A 16%, transparent); color: #2D7D5A; }
    .rot-dc--c { background: color-mix(in srgb, #4A90C4 16%, transparent); color: #4A90C4; }

    /* Seção (cards) */
    .rot-sec { margin-bottom: 1.5rem; }
    .rot-sec-head { display: flex; align-items: center; gap: .5rem; margin-bottom: .75rem; padding-bottom: .45rem; border-bottom: 1px solid var(--surface-border); }
    .rot-sec-ico { font-size: 1rem; color: var(--text-color-secondary); }
    .rot-sec-nome { font-weight: 600; font-size: .95rem; }
    .rot-sec-slug { font-size: .7rem; color: var(--text-color-secondary); }
    .rot-sec-count { margin-left: auto; font-size: .75rem; color: var(--text-color-secondary); }

    .rot-cards-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: .85rem; }
    .rot-card { border: 0.5px solid var(--surface-border); border-radius: 10px; padding: .75rem .85rem;
      background: var(--surface-card, var(--surface-0)); display: flex; flex-direction: column; gap: .5rem;
      border-left: 3px solid var(--primary-color); transition: box-shadow .12s ease, transform .12s ease; }
    .rot-card:hover { box-shadow: 0 2px 10px rgba(0,0,0,.07); transform: translateY(-1px); }
    .rot-card-head { display: flex; align-items: center; justify-content: space-between; gap: .5rem; }
    .rot-prio-badge { flex-shrink: 0; font-size: .68rem; font-weight: 700; color: var(--text-color-secondary);
      background: var(--surface-100); border-radius: 6px; padding: .1rem .4rem; }
    .rot-card-parts { display: flex; flex-direction: column; gap: .3rem; }
    .rot-part-line { display: flex; align-items: center; gap: .4rem; font-size: .85rem; color: var(--text-color-secondary); }
    .rot-part-line b { color: var(--text-color); font-weight: 500; }
    .rot-card-foot { display: flex; justify-content: flex-end; border-top: 1px solid var(--surface-border); padding-top: .4rem; }

    /* Lista (agrupada) */
    .rot-lista { border: 0.5px solid var(--surface-border); border-radius: 12px; overflow: hidden; }
    .grp-head { display: flex; align-items: center; gap: .5rem; padding: .55rem .85rem; background: var(--surface-100); border-bottom: 0.5px solid var(--surface-border); }
    .grp-ico { font-size: 1rem; color: var(--text-color-secondary); }
    .grp-nome { font-weight: 600; }
    .grp-slug { font-size: .7rem; color: var(--text-color-secondary); }
    .grp-count { margin-left: auto; font-size: .78rem; color: var(--text-color-secondary); }
    .rot-row { padding: .6rem .85rem; border-bottom: 0.5px solid var(--surface-border); }
    .rot-row:last-child { border-bottom: none; }
    .rot-top { display: flex; align-items: center; justify-content: space-between; gap: .5rem; margin-bottom: .35rem; }
    .rot-right { display: inline-flex; align-items: center; gap: .75rem; flex-shrink: 0; }
    .rot-prio { font-size: .75rem; color: var(--text-color-secondary); }
    .rot-parts { display: flex; align-items: center; gap: 1.1rem; flex-wrap: wrap; }
    .rot-part { font-size: .82rem; color: var(--text-color-secondary); display: inline-flex; align-items: center; gap: .3rem; }
    .rot-part b { color: var(--text-color); font-weight: 500; }

    /* Tabela */
    .rot-card-wrap { border: 0.5px solid var(--surface-border); border-radius: 12px; overflow: hidden; background: var(--surface-card, var(--surface-0)); }
    .tab-ev { display: inline-flex; align-items: center; gap: .4rem; font-weight: 500; }
    .tab-ev i { color: var(--text-color-secondary); }
    .tab-part { font-size: .85rem; }
    .tab-part b { font-weight: 500; }

    /* Dialog */
    .dlg-form { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem 1.25rem; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field--full { grid-column: 1 / -1; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .dlg-hint { font-size: .75rem; color: var(--text-color-secondary); }
    .part-head { display: flex; align-items: center; justify-content: space-between; margin: 1.25rem 0 .5rem; }
    .part-head label { font-size: .8rem; color: var(--text-color-secondary); }
    .part-linha { display: flex; align-items: center; gap: .5rem; margin-bottom: .5rem; }
    :host ::ng-deep .part-tipo { width: 6rem; }
    :host ::ng-deep .part-conta { flex: 1; }
    .dlg-previa { margin: .75rem 0 0; font-size: .82rem; color: var(--text-color); background: var(--surface-100);
      border-left: 3px solid var(--primary-color); border-radius: 0; padding: .5rem .75rem; }
    .dlg-dica { margin: 1rem 0 0; font-size: .8rem; color: var(--text-color-secondary); }
    .w-full { width: 100%; }
  `],
})
export class ConfigRoteirosTabComponent implements OnInit {
  private service = inject(ContabilidadeService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);
  private confirm = inject(ConfirmationService);

  loading = signal(true);
  salvando = signal(false);
  roteiros = signal<Roteiro[]>([]);
  contas = signal<Conta[]>([]);
  filtro = signal('');
  visualizacao = signal<Visualizacao>('cards');
  dialogVisivel = signal(false);
  linhas = signal<LinhaPartida[]>([]);

  eventoSel = signal<CatalogoEvento | null>(null);
  condValores = signal<Record<string, string>>({});                                 // chave -> valor ('' = Qualquer)
  opcoesDim = signal<Record<string, { label: string; value: string }[]>>({});       // cadastroRef -> options
  historico = '';

  readonly catalogo = CATALOGO_EVENTOS;
  readonly opcoesVis = [
    { label: 'Cards', value: 'cards' },
    { label: 'Lista', value: 'lista' },
    { label: 'Tabela', value: 'tabela' },
  ];
  readonly tipos = [
    { label: 'Débito (D)', value: 'D' },
    { label: 'Crédito (C)', value: 'C' },
  ];

  contasOpcoes = computed(() =>
    this.contas()
      .filter((c) => c.aceitaLancamento)
      .map((c) => ({ label: c.codigo + ' — ' + c.nome, value: c.codigo })),
  );

  // Totalizadores.
  cards = computed<CardStat[]>(() => {
    const rs = this.roteiros();
    const eventos = new Set(rs.map((r) => r.eventoTipo)).size;
    const comCond = rs.filter((r) => this.chips(r).length > 0).length;
    return [
      { label: 'Roteiros ativos', value: rs.length, icon: 'pi pi-bolt', color: '#4A90C4', bg: 'color-mix(in srgb, #4A90C4 12%, transparent)' },
      { label: 'Eventos cobertos', value: eventos, icon: 'pi pi-sitemap', color: '#2D7D5A', bg: 'color-mix(in srgb, #2D7D5A 12%, transparent)' },
      { label: 'Com condição', value: comCond, icon: 'pi pi-filter', color: '#8A6D3B', bg: 'color-mix(in srgb, #8A6D3B 12%, transparent)' },
    ];
  });

  private roteirosFiltrados = computed<Roteiro[]>(() => {
    const termo = this.filtro().trim().toLowerCase();
    if (!termo) return this.roteiros();
    return this.roteiros().filter((r) => {
      const ev = (this.rotuloEvento(r.eventoTipo) + ' ' + r.eventoTipo).toLowerCase();
      const cond = this.chips(r).join(' ').toLowerCase();
      const parts = r.partidas.map((p) => `${p.contaNome ?? ''} ${p.contaCampo ?? ''} ${p.contaCodigo ?? ''}`).join(' ').toLowerCase();
      return ev.includes(termo) || cond.includes(termo) || parts.includes(termo);
    });
  });

  // Roteiros agrupados por evento (conhecidos primeiro; itens por prioridade desc).
  grupos = computed<GrupoRoteiros[]>(() => {
    const ordem = EVENTOS_CONHECIDOS.map((e) => e.tipo);
    const mapa = new Map<string, Roteiro[]>();
    for (const r of this.roteirosFiltrados()) {
      if (!mapa.has(r.eventoTipo)) mapa.set(r.eventoTipo, []);
      mapa.get(r.eventoTipo)!.push(r);
    }
    return [...mapa.entries()]
      .map(([tipo, itens]) => ({
        tipo,
        rotulo: this.rotuloEvento(tipo),
        icone: this.iconeEvento(tipo),
        itens: [...itens].sort((a, b) => b.prioridade - a.prioridade),
      }))
      .sort((a, b) => {
        const ia = ordem.indexOf(a.tipo), ib = ordem.indexOf(b.tipo);
        const pa = ia === -1 ? 99 : ia, pb = ib === -1 ? 99 : ib;
        return pa !== pb ? pa - pb : a.rotulo.localeCompare(b.rotulo);
      });
  });

  // Linhas planas para a tabela (mantém ordem dos grupos).
  tabela = computed<TabelaRow[]>(() => {
    const rows: TabelaRow[] = [];
    for (const g of this.grupos()) {
      for (const r of g.itens) rows.push({ roteiro: r, evRotulo: g.rotulo, evIcone: g.icone });
    }
    return rows;
  });

  rotuloEvento(tipo: string): string {
    return EVENTOS_CONHECIDOS.find((e) => e.tipo === tipo)?.rotulo ?? tipo;
  }

  iconeEvento(tipo: string): string {
    return EVENTOS_CONHECIDOS.find((e) => e.tipo === tipo)?.icone ?? 'pi pi-bolt';
  }

  parts(r: Roteiro, tipo: 'D' | 'C'): Partida[] {
    return r.partidas
      .filter((p) => p.tipo === tipo)
      .map((p) => ({ nome: p.contaNome || p.contaCampo || '?', codigo: p.contaCodigo }));
  }

  chips(r: Roteiro): string[] {
    if (!r.condicoes) return [];
    try {
      const obj = JSON.parse(r.condicoes) as Record<string, unknown>;
      return Object.values(obj).map((v) => VALOR_ROTULO[String(v)] ?? String(v));
    } catch {
      return [];
    }
  }

  // Prévia em linguagem natural pro dialog.
  previa(): string {
    const ev = this.eventoSel();
    const ls = this.linhas().filter((l) => l.contaCodigo);
    if (!ev || ls.length < 2) return '';
    const nome = (cod: string) => this.contas().find((c) => c.codigo === cod)?.nome ?? cod;
    const deb = ls.filter((l) => l.tipo === 'D').map((l) => nome(l.contaCodigo)).join(', ');
    const cred = ls.filter((l) => l.tipo === 'C').map((l) => nome(l.contaCodigo)).join(', ');
    if (!deb || !cred) return '';
    const conds = Object.entries(this.condValores()).filter(([, v]) => v).map(([, v]) => v);
    const quando = conds.length ? ` (${conds.join(' · ')})` : '';
    return `Quando "${ev.rotulo}"${quando} acontecer: debita ${deb} e credita ${cred}.`;
  }

  ngOnInit(): void {
    this.carregar();
    this.service.listarContas().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (contas) => this.contas.set(contas),
      error: () => { /* selects ficam vazios; erro do plano já é sinalizado na outra aba */ },
    });
  }

  carregar(): void {
    this.loading.set(true);
    this.service.listarRoteiros().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (r) => this.roteiros.set(r),
      error: () => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: 'Falha ao carregar os roteiros.' }),
    });
  }

  abrirNovo(): void {
    this.eventoSel.set(null);
    this.condValores.set({});
    this.historico = '';
    this.linhas.set([{ tipo: 'D', contaCodigo: '' }, { tipo: 'C', contaCodigo: '' }]);
    this.dialogVisivel.set(true);
  }

  selecionarEvento(ev: CatalogoEvento): void {
    this.eventoSel.set(ev);
    this.condValores.set({});
    // As opções de condição por cadastro (forma de pagamento, tipo de despesa) vêm do ERP
    // e ficam FORA deste app standalone (fronteira da extração). Aqui a condição fica em
    // "Qualquer" — o roteiro vale para o evento independentemente da forma/tipo. A regra
    // específica por forma/tipo continua sendo criada no ERP.
    for (const d of ev.dimensoes) {
      if (!d.cadastroRef || this.opcoesDim()[d.cadastroRef]) continue;
      this.opcoesDim.update((m) => ({ ...m, [d.cadastroRef]: [{ label: 'Qualquer', value: '' }] }));
    }
  }

  setCond(chave: string, valor: string): void {
    this.condValores.update((m) => ({ ...m, [chave]: valor }));
  }

  addLinha(): void {
    this.linhas.update((ls) => [...ls, { tipo: 'D', contaCodigo: '' }]);
  }

  removerLinha(i: number): void {
    if (this.linhas().length <= 2) return;
    this.linhas.update((ls) => ls.filter((_, idx) => idx !== i));
  }

  podeSalvar(): boolean {
    const ls = this.linhas();
    if (!this.eventoSel() || ls.length < 2) return false;
    const temD = ls.some((l) => l.tipo === 'D');
    const temC = ls.some((l) => l.tipo === 'C');
    const todasComConta = ls.every((l) => !!l.contaCodigo);
    return temD && temC && todasComConta;
  }

  salvar(): void {
    if (!this.podeSalvar()) return;
    const condicoes: Record<string, string> = {};
    for (const [k, v] of Object.entries(this.condValores())) if (v) condicoes[k] = v;
    const temCond = Object.keys(condicoes).length > 0;
    const dto: RoteiroCreate = {
      eventoTipo: this.eventoSel()!.codigo,
      prioridade: temCond ? 20 : 10,   // regra com condição ganha da regra padrão
      historicoTemplate: this.historico?.trim() || undefined,
      condicoes: temCond ? condicoes : undefined,
      partidas: this.linhas().map((l) => ({
        tipo: l.tipo,
        contaModo: 'constante',
        contaCodigo: l.contaCodigo,
        base: 'valor_total',
      })),
    };
    this.salvando.set(true);
    this.service.criarRoteiro(dto).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.salvando.set(false)),
    ).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Contabilidade', detail: 'Roteiro criado.' });
        this.dialogVisivel.set(false);
        this.carregar();
      },
      error: (err) => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: err?.error?.mensagem || err?.error?.message || 'Falha ao criar o roteiro.' }),
    });
  }

  confirmarDesativar(r: Roteiro): void {
    this.confirm.confirm({
      message: `Desativar o roteiro de "${this.rotuloEvento(r.eventoTipo)}"? Ele deixa de contabilizar novos eventos desse tipo.`,
      header: 'Confirmar desativação',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.service.excluirRoteiro(r.id).pipe(
          takeUntilDestroyed(this.destroyRef),
        ).subscribe({
          next: () => {
            this.msg.add({ severity: 'success', summary: 'Contabilidade', detail: 'Roteiro desativado.' });
            this.carregar();
          },
          error: (err) => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: err?.error?.mensagem || err?.error?.message || 'Falha ao desativar o roteiro.' }),
        });
      },
    });
  }
}
