import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService, TreeNode } from 'primeng/api';
import { finalize } from 'rxjs';
import { PRIMENG_MODULES } from '../../shared/primeng';
import { BotaoComponent } from '../../shared/components/botao/botao.component';
import { BotaoAcaoComponent } from '../../shared/components/botao/botao-acao.component';
import { ContaArvore, ContabilidadeService } from '../../shared/services/contabilidade.service';
import { CardStat } from '../../shared/models';

interface ContaForm {
  codigo: string;
  nome: string;
  tipo: string;
  natureza: string;
  grupo: string;
  retificadora: boolean;
}

/** Conta analítica (folha) com o nome do grupo sintético pai/raiz, para exibição em card/tabela. */
interface ContaLeaf {
  conta: ContaArvore;
  grupoPai: string;
}

/** Seção do plano: um grupo raiz (Ativo, Passivo/PL, ...) e suas contas analíticas. */
interface GrupoContas {
  codigo: string;
  nome: string;
  contas: ContaLeaf[];
}

/** Linha da visualização em tabela (conta analítica + grupo raiz). */
interface ContaRow {
  conta: ContaArvore;
  grupo: string;
}

type Visualizacao = 'cards' | 'arvore' | 'tabela';

/**
 * Plano de contas com CRUD e três visualizações (toggle no topo):
 *  - Cards: contas analíticas como cartões, agrupadas pela sintética raiz.
 *  - Árvore: hierarquia completa do plano (sintéticas + analíticas).
 *  - Tabela: lista compacta das contas analíticas.
 * Só contas analíticas (folha) recebem lançamento. Exclusão é soft e bloqueada (409) quando a
 * conta já tem lançamento. A busca por código/nome atua nas três visualizações.
 */
@Component({
  selector: 'app-config-plano-contas-tab',
  standalone: true,
  imports: [FormsModule, ...PRIMENG_MODULES, BotaoComponent, BotaoAcaoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="plano-head">
      <p class="plano-info">Plano de contas do módulo contábil. Só contas analíticas recebem lançamento.</p>
      <app-botao label="Nova conta" icon="pi pi-plus" size="small" (clicado)="abrirNova()" />
    </div>

    <!-- Cards resumo -->
    @if (!loading() && arvore().length > 0) {
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
      <p>Carregando plano de contas...</p>
    } @else if (arvore().length === 0) {
      <p class="vazio">Nenhuma conta encontrada. Verifique se o módulo contábil está habilitado.</p>
    } @else {
      <!-- Toolbar: busca + toggle de visualização -->
      <div class="plano-toolbar">
        <div class="busca-wrap">
          <i class="pi pi-search"></i>
          <input pInputText [ngModel]="filtro()" (ngModelChange)="filtro.set($event)"
                 placeholder="Buscar conta por código ou nome" class="w-full" />
        </div>
        <p-selectButton [ngModel]="visualizacao()" (ngModelChange)="visualizacao.set($event)"
                        [options]="opcoesVis" optionLabel="label" optionValue="value"
                        [allowEmpty]="false" styleClass="vis-toggle" />
      </div>

      <!-- ── Visualização: CARDS ── -->
      @if (visualizacao() === 'cards') {
        @if (gruposFiltrados().length === 0) {
          <p class="vazio">Nenhuma conta corresponde à busca.</p>
        } @else {
          @for (g of gruposFiltrados(); track g.codigo) {
            <section class="grupo-sec">
              <header class="grupo-sec-head">
                <span class="conta-cod">{{ g.codigo }}</span>
                <span class="grupo-nome">{{ g.nome }}</span>
                <span class="grupo-count">{{ g.contas.length }} {{ g.contas.length === 1 ? 'conta' : 'contas' }}</span>
              </header>
              <div class="contas-grid">
                @for (c of g.contas; track c.conta.id) {
                  <div class="conta-card" [class.conta-card--credora]="c.conta.natureza === 'C'">
                    <div class="conta-card-top">
                      <span class="conta-cod">{{ c.conta.codigo }}</span>
                      <p-tag [value]="c.conta.natureza === 'C' ? 'Credora' : 'Devedora'"
                             [severity]="c.conta.natureza === 'C' ? 'info' : 'success'" styleClass="conta-nat" />
                    </div>
                    <div class="conta-card-nome">{{ c.conta.nome }}</div>
                    @if (c.grupoPai) { <div class="conta-card-pai"><i class="pi pi-folder"></i> {{ c.grupoPai }}</div> }
                    <div class="conta-card-acoes">
                      <app-botao-acao acao="editar" tooltip="Editar conta" (clicado)="abrirEdicao(c.conta)" />
                      <app-botao-acao acao="excluir" tooltip="Excluir conta" (clicado)="confirmarExclusao(c.conta)" />
                    </div>
                  </div>
                }
              </div>
            </section>
          }
        }
      }

      <!-- ── Visualização: ÁRVORE ── -->
      @if (visualizacao() === 'arvore') {
        @if (nodesFiltrados().length === 0) {
          <p class="vazio">Nenhuma conta corresponde à busca.</p>
        } @else {
          <div class="plano-card">
            <p-tree [value]="nodesFiltrados()" styleClass="plano-tree">
              <ng-template let-node pTemplate="default">
                <span class="conta-linha">
                  <span class="conta-cod">{{ node.data.codigo }}</span>
                  <span class="conta-nome">{{ node.data.nome }}</span>
                  @if (node.data.aceitaLancamento) {
                    <p-tag [value]="node.data.natureza === 'C' ? 'Credora' : 'Devedora'"
                           [severity]="node.data.natureza === 'C' ? 'info' : 'success'" styleClass="conta-nat" />
                    <span class="conta-acoes">
                      <app-botao-acao acao="editar" tooltip="Editar conta" (clicado)="abrirEdicao(node.data)" />
                      <app-botao-acao acao="excluir" tooltip="Excluir conta" (clicado)="confirmarExclusao(node.data)" />
                    </span>
                  } @else {
                    <span class="conta-grupo-tag">grupo</span>
                  }
                </span>
              </ng-template>
            </p-tree>
          </div>
        }
      }

      <!-- ── Visualização: TABELA ── -->
      @if (visualizacao() === 'tabela') {
        <div class="plano-card">
          <p-table [value]="contasTabela()" dataKey="conta.id" [tableStyle]="{'min-width': '45rem'}" styleClass="p-datatable-sm">
            <ng-template #header>
              <tr>
                <th style="width:130px">Código</th>
                <th>Conta</th>
                <th style="width:220px">Grupo</th>
                <th style="width:110px">Natureza</th>
                <th style="width:90px">Ações</th>
              </tr>
            </ng-template>
            <ng-template #body let-row>
              <tr>
                <td><span class="conta-cod">{{ row.conta.codigo }}</span></td>
                <td>{{ row.conta.nome }}</td>
                <td class="tab-grupo">{{ row.grupo }}</td>
                <td>
                  <p-tag [value]="row.conta.natureza === 'C' ? 'Credora' : 'Devedora'"
                         [severity]="row.conta.natureza === 'C' ? 'info' : 'success'" styleClass="conta-nat" />
                </td>
                <td>
                  <span class="conta-acoes">
                    <app-botao-acao acao="editar" tooltip="Editar conta" (clicado)="abrirEdicao(row.conta)" />
                    <app-botao-acao acao="excluir" tooltip="Excluir conta" (clicado)="confirmarExclusao(row.conta)" />
                  </span>
                </td>
              </tr>
            </ng-template>
            <ng-template #emptymessage>
              <tr><td colspan="5" class="vazio">Nenhuma conta corresponde à busca.</td></tr>
            </ng-template>
          </p-table>
        </div>
      }
    }

    <p-dialog [visible]="dialogVisivel()" (visibleChange)="dialogVisivel.set($event)"
              [modal]="true" [header]="editando() ? 'Editar conta' : 'Nova conta'" [style]="{ width: '34rem' }">
      <div class="dlg-form">
        <div class="field">
          <label>Código</label>
          <input pInputText [(ngModel)]="form.codigo" [disabled]="editando()" placeholder="Ex.: 3.1.1.05" class="w-full" />
        </div>
        <div class="field">
          <label>Nome</label>
          <input pInputText [(ngModel)]="form.nome" placeholder="Nome da conta" class="w-full" />
        </div>
        <div class="field">
          <label>Tipo</label>
          <p-select [(ngModel)]="form.tipo" [options]="tipos" optionLabel="label" optionValue="value"
                    [disabled]="editando()" appendTo="body" styleClass="w-full" />
        </div>
        <div class="field">
          <label>Natureza</label>
          <p-select [(ngModel)]="form.natureza" [options]="naturezas" optionLabel="label" optionValue="value"
                    appendTo="body" styleClass="w-full" />
        </div>
        <div class="field">
          <label>Grupo</label>
          <p-select [(ngModel)]="form.grupo" [options]="grupos_" optionLabel="label" optionValue="value"
                    [disabled]="editando()" appendTo="body" styleClass="w-full" />
        </div>
        <div class="field field--check">
          <p-checkbox [(ngModel)]="form.retificadora" [binary]="true" inputId="retificadora" />
          <label for="retificadora">Conta retificadora</label>
        </div>
      </div>
      <ng-template #footer>
        <app-botao label="Cancelar" [text]="true" severity="secondary" (clicado)="dialogVisivel.set(false)" />
        <app-botao [label]="editando() ? 'Salvar' : 'Criar'" icon="pi pi-check" [loading]="salvando()"
                   [disabled]="!podeSalvar()" (clicado)="salvar()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    :host { display: block; }
    .plano-head { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: 1rem; }
    .plano-info { margin: 0; font-size: .85rem; color: var(--text-color-secondary); }
    .vazio { color: var(--text-color-secondary); }

    /* Cards resumo — mesmo padrão do Financeiro */
    .cards-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .card-stat { display: flex; align-items: center; gap: 1rem; padding: .25rem 0; }
    .card-stat-icon { width: 50px; height: 50px; border-radius: 12px; display: flex; align-items: center;
      justify-content: center; flex-shrink: 0; }
    .card-stat-icon i { font-size: 1.4rem; }
    .card-stat-info { display: flex; flex-direction: column; }
    .card-stat-value { font-family: var(--font-display, inherit); font-size: 1.8rem; font-weight: 800; letter-spacing: var(--tracking-tight, -.02em); }
    .card-stat-label { font-family: var(--font-body, inherit); font-size: .75rem; color: var(--p-text-muted-color, var(--text-color-secondary));
      text-transform: uppercase; letter-spacing: var(--tracking-wide, .04em); font-weight: 500; }

    /* Toolbar: busca + toggle */
    .plano-toolbar { display: flex; align-items: center; gap: 1rem; margin-bottom: 1.25rem; flex-wrap: wrap; }
    .busca-wrap { position: relative; flex: 1; min-width: 220px; }
    .busca-wrap i { position: absolute; left: .75rem; top: 50%; transform: translateY(-50%);
      color: var(--text-color-secondary); font-size: .9rem; pointer-events: none; }
    .busca-wrap input { padding-left: 2.25rem; }
    :host ::ng-deep .vis-toggle { flex-shrink: 0; }

    /* Seção de grupo sintético (cards) */
    .grupo-sec { margin-bottom: 1.75rem; }
    .grupo-sec-head { display: flex; align-items: center; gap: .5rem; margin-bottom: .75rem;
      padding-bottom: .45rem; border-bottom: 1px solid var(--surface-border); }
    .grupo-nome { font-weight: 600; font-size: .95rem; }
    .grupo-count { margin-left: auto; font-size: .75rem; color: var(--text-color-secondary); }

    .contas-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: .85rem; }
    .conta-card { border: 0.5px solid var(--surface-border); border-radius: 10px; padding: .7rem .85rem;
      background: var(--surface-card, var(--surface-0)); display: flex; flex-direction: column; gap: .3rem;
      border-left: 3px solid #2D7D5A; transition: border-color .12s ease, box-shadow .12s ease, transform .12s ease; }
    .conta-card--credora { border-left-color: #4A90C4; }
    .conta-card:hover { box-shadow: 0 2px 10px rgba(0,0,0,.07); transform: translateY(-1px); }
    .conta-card-top { display: flex; align-items: center; justify-content: space-between; gap: .5rem; }
    .conta-cod { font-family: var(--font-family-mono, monospace); color: var(--text-color-secondary);
      background: var(--surface-100); padding: .05rem .4rem; border-radius: 5px; font-size: .78rem; }
    .conta-card-nome { font-weight: 500; color: var(--text-color); font-size: .92rem; line-height: 1.25; }
    .conta-card-pai { font-size: .72rem; color: var(--text-color-secondary); display: flex; align-items: center; gap: .3rem; }
    .conta-card-pai i { font-size: .72rem; }
    .conta-card-acoes { display: flex; justify-content: flex-end; gap: .1rem; margin-top: .15rem;
      border-top: 1px solid var(--surface-border); padding-top: .35rem; }
    :host ::ng-deep .conta-nat { font-size: .68rem; }

    /* Container compartilhado (árvore + tabela) */
    .plano-card { border: 0.5px solid var(--surface-border); border-radius: 12px; overflow: hidden;
      background: var(--surface-card, var(--surface-0)); }
    :host ::ng-deep .plano-tree { border: none; padding: .5rem; }
    :host ::ng-deep .plano-tree .p-treenode-content,
    :host ::ng-deep .plano-tree .p-tree-node-content { border-radius: 8px; transition: background .12s ease; }
    :host ::ng-deep .plano-tree .p-treenode-content:hover,
    :host ::ng-deep .plano-tree .p-tree-node-content:hover { background: var(--surface-100); }
    /* Tabulação por nível: PrimeNG v21 zera o recuo dos filhos; recuamos com linha-guia. */
    :host ::ng-deep .plano-tree .p-tree-node-children {
      padding-inline-start: 1.5rem; margin-inline-start: .65rem;
      border-inline-start: 1px dashed var(--surface-border);
    }
    .conta-linha { display: inline-flex; align-items: center; gap: .5rem; width: 100%; }
    .conta-nome { color: var(--text-color); }
    .conta-grupo-tag { font-size: .68rem; text-transform: uppercase; letter-spacing: .04em;
      color: var(--text-color-secondary); opacity: .6; }
    .conta-acoes { margin-left: auto; display: inline-flex; gap: .1rem; align-items: center; }
    .tab-grupo { color: var(--text-color-secondary); font-size: .85rem; }

    .dlg-form { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem 1.25rem; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field--check { grid-column: 1 / -1; flex-direction: row; align-items: center; gap: .5rem; }
    .field label { font-size: .8rem; color: var(--text-color-secondary); }
    .field--check label { font-size: .9rem; color: var(--text-color); }
    .w-full { width: 100%; }
  `],
})
export class ConfigPlanoContasTabComponent implements OnInit {
  private service = inject(ContabilidadeService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);
  private confirm = inject(ConfirmationService);

  loading = signal(true);
  arvore = signal<ContaArvore[]>([]);
  filtro = signal('');
  visualizacao = signal<Visualizacao>('cards');
  dialogVisivel = signal(false);
  editando = signal(false);
  salvando = signal(false);

  readonly opcoesVis = [
    { label: 'Cards', value: 'cards' },
    { label: 'Árvore', value: 'arvore' },
    { label: 'Tabela', value: 'tabela' },
  ];

  // Cards de resumo (contagem por natureza sintética/analítica).
  cards = computed<CardStat[]>(() => {
    let total = 0;
    let analiticas = 0;
    const contar = (lista: ContaArvore[]): void => {
      for (const c of lista) {
        total++;
        if (c.aceitaLancamento) analiticas++;
        if (c.filhos?.length) contar(c.filhos);
      }
    };
    contar(this.arvore());
    return [
      { label: 'Total de contas', value: total, icon: 'pi pi-sitemap', color: '#4A90C4', bg: 'color-mix(in srgb, #4A90C4 12%, transparent)' },
      { label: 'Analíticas (recebem lançamento)', value: analiticas, icon: 'pi pi-file', color: '#2D7D5A', bg: 'color-mix(in srgb, #2D7D5A 12%, transparent)' },
      { label: 'Sintéticas (agrupadoras)', value: total - analiticas, icon: 'pi pi-folder', color: '#8A6D3B', bg: 'color-mix(in srgb, #8A6D3B 12%, transparent)' },
    ];
  });

  // Seções (grupo raiz → contas analíticas) para a visualização em cards.
  grupos = computed<GrupoContas[]>(() => {
    const grupos: GrupoContas[] = [];
    for (const raiz of this.arvore()) {
      const contas: ContaLeaf[] = [];
      const coletar = (node: ContaArvore, paiNome: string): void => {
        if (node.aceitaLancamento) contas.push({ conta: node, grupoPai: paiNome });
        for (const f of node.filhos ?? []) coletar(f, node.nome);
      };
      coletar(raiz, '');
      if (contas.length) grupos.push({ codigo: raiz.codigo, nome: raiz.nome, contas });
    }
    return grupos;
  });

  gruposFiltrados = computed<GrupoContas[]>(() => {
    const termo = this.filtro().trim().toLowerCase();
    if (!termo) return this.grupos();
    return this.grupos()
      .map((g) => ({ ...g, contas: g.contas.filter((c) => this.casa(c.conta, termo)) }))
      .filter((g) => g.contas.length > 0);
  });

  // Linhas planas (conta analítica + grupo raiz) para a visualização em tabela.
  contasTabela = computed<ContaRow[]>(() => {
    const termo = this.filtro().trim().toLowerCase();
    const rows: ContaRow[] = [];
    for (const g of this.grupos()) {
      for (const c of g.contas) {
        if (!termo || this.casa(c.conta, termo)) rows.push({ conta: c.conta, grupo: g.nome });
      }
    }
    return rows;
  });

  // Nós da árvore, filtrados por código/nome (mantém ancestrais dos matches).
  nodesFiltrados = computed<TreeNode[]>(() => {
    const termo = this.filtro().trim().toLowerCase();
    const base = termo ? this.filtrarArvore(this.arvore(), termo) : this.arvore();
    return base.map((c) => this.toNode(c, !!termo));
  });

  private editandoId: number | null = null;
  form: ContaForm = this.formVazio();

  readonly tipos = [
    { label: 'Sintética', value: 'sintetica' },
    { label: 'Analítica', value: 'analitica' },
  ];
  readonly naturezas = [
    { label: 'Devedora (D)', value: 'D' },
    { label: 'Credora (C)', value: 'C' },
  ];
  readonly grupos_ = [
    { label: 'Ativo', value: 'ativo' },
    { label: 'Passivo', value: 'passivo' },
    { label: 'Patrimônio Líquido', value: 'pl' },
    { label: 'Receita', value: 'receita' },
    { label: 'Custo', value: 'custo' },
    { label: 'Despesa', value: 'despesa' },
  ];

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.loading.set(true);
    this.service.arvoreContas().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (arvore) => this.arvore.set(arvore ?? []),
      error: () => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: 'Falha ao carregar o plano de contas.' }),
    });
  }

  private casa(c: ContaArvore, termo: string): boolean {
    return c.codigo.toLowerCase().includes(termo) || c.nome.toLowerCase().includes(termo);
  }

  private filtrarArvore(lista: ContaArvore[], termo: string): ContaArvore[] {
    const res: ContaArvore[] = [];
    for (const n of lista) {
      const filhos = this.filtrarArvore(n.filhos ?? [], termo);
      if (this.casa(n, termo) || filhos.length) res.push({ ...n, filhos });
    }
    return res;
  }

  private toNode(c: ContaArvore, expandirTudo: boolean): TreeNode {
    return {
      key: c.codigo,
      label: c.nome,
      data: c,
      icon: c.aceitaLancamento ? 'pi pi-file' : 'pi pi-folder',
      expanded: expandirTudo || (c.codigo.match(/\./g)?.length ?? 0) < 1,
      children: (c.filhos ?? []).map((f) => this.toNode(f, expandirTudo)),
    };
  }

  abrirNova(): void {
    this.editando.set(false);
    this.editandoId = null;
    this.form = this.formVazio();
    this.dialogVisivel.set(true);
  }

  abrirEdicao(c: ContaArvore): void {
    this.editando.set(true);
    this.editandoId = c.id;
    this.form = {
      codigo: c.codigo,
      nome: c.nome,
      tipo: c.tipo,
      natureza: c.natureza,
      grupo: '',
      retificadora: false,
    };
    this.dialogVisivel.set(true);
  }

  podeSalvar(): boolean {
    if (!this.form.nome?.trim() || !this.form.natureza) return false;
    if (this.editando()) return true;
    return !!this.form.codigo?.trim() && !!this.form.tipo && !!this.form.grupo;
  }

  salvar(): void {
    if (!this.podeSalvar()) return;
    this.salvando.set(true);
    const obs = this.editando()
      ? this.service.atualizarConta(this.editandoId!, {
          nome: this.form.nome.trim(),
          natureza: this.form.natureza,
          retificadora: this.form.retificadora,
        })
      : this.service.criarConta({
          codigo: this.form.codigo.trim(),
          nome: this.form.nome.trim(),
          tipo: this.form.tipo,
          natureza: this.form.natureza,
          grupo: this.form.grupo,
          retificadora: this.form.retificadora,
        });

    obs.pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.salvando.set(false)),
    ).subscribe({
      next: () => {
        this.msg.add({ severity: 'success', summary: 'Contabilidade', detail: this.editando() ? 'Conta atualizada.' : 'Conta criada.' });
        this.dialogVisivel.set(false);
        this.carregar();
      },
      error: (err) => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: err?.error?.mensagem || err?.error?.message || 'Falha ao salvar a conta.' }),
    });
  }

  confirmarExclusao(c: ContaArvore): void {
    this.confirm.confirm({
      message: `Deseja realmente excluir a conta "${c.codigo} — ${c.nome}"?`,
      header: 'Confirmar exclusão',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.service.excluirConta(c.id).pipe(
          takeUntilDestroyed(this.destroyRef),
        ).subscribe({
          next: () => {
            this.msg.add({ severity: 'success', summary: 'Contabilidade', detail: 'Conta excluída.' });
            this.carregar();
          },
          error: (err) => this.msg.add({ severity: 'error', summary: 'Contabilidade', detail: err?.error?.mensagem || err?.error?.message || err?.error?.error || 'Falha ao excluir a conta.' }),
        });
      },
    });
  }

  private formVazio(): ContaForm {
    return { codigo: '', nome: '', tipo: 'analitica', natureza: 'D', grupo: 'receita', retificadora: false };
  }
}
