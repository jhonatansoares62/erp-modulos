import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../../core/api.config';

// ── Contas / Plano de contas ──
export interface ContaArvore {
  id: number;
  codigo: string;
  nome: string;
  tipo: string;        // sintetica | analitica
  natureza: string;    // D | C
  aceitaLancamento: boolean;
  filhos: ContaArvore[];
}

export interface Conta {
  id: number;
  codigo: string;
  nome: string;
  tipo: string;
  natureza: string;
  aceitaLancamento: boolean;
}

export interface ContaCreate {
  codigo: string;
  nome: string;
  tipo: string;
  natureza: string;
  grupo: string;
  retificadora: boolean;
}

export interface ContaUpdate {
  nome: string;
  natureza: string;
  retificadora: boolean;
}

// ── Pendências / Regras / Roteiros ──
export interface Pendencia {
  eventoId: string;
  tipo: string;
  origem: string;
  dataEvento: string;
  valorCentavos: number;
  recebidoEm: string;
}

export interface PartidaRegra {
  tipo: 'D' | 'C';
  contaModo?: string;
  contaCodigo: string;
  base?: string;
}

export interface RegraCreate {
  historicoTemplate?: string;
  condicoes?: Record<string, unknown>;
  partidas: PartidaRegra[];
}

export interface RoteiroPartida {
  tipo: 'D' | 'C';
  contaModo?: string;
  contaCodigo?: string;
  contaNome?: string;
  contaCampo?: string;
  base?: string;
  percentual?: number;
}

export interface Roteiro {
  id: number;
  eventoTipo: string;
  prioridade: number;
  condicoes?: string;
  historicoTemplate?: string;
  vigenciaInicio?: string;
  vigenciaFim?: string;
  ativo: boolean;
  partidas: RoteiroPartida[];
}

export interface RoteiroCreate {
  eventoTipo: string;
  prioridade?: number;
  historicoTemplate?: string;
  condicoes?: Record<string, unknown>;
  partidas: PartidaRegra[];
}

// ── Relatórios ──
export interface BalanceteLinha {
  codigo: string;
  nome: string;
  debitos: number;
  creditos: number;
  saldoCentavos: number;
  saldoNatureza: string;
}

export interface Balancete {
  de: string;
  ate: string;
  linhas: BalanceteLinha[];
  totalDebitos: number;
  totalCreditos: number;
  fecha: boolean;
}

export interface Dre {
  de: string;
  ate: string;
  receitaBruta: number;
  deducoes: number;
  devolucoes: number;
  receitaLiquida: number;
  custos: number;
  lucroBruto: number;
  despesasOperacionais: number;
  despesasFinanceiras: number;
  receitasFinanceiras: number;
  resultadoLiquido: number;
}

export interface BalancoLinha {
  codigo: string;
  nome: string;
  saldoCentavos: number;
}

export interface Balanco {
  data: string;
  ativo: BalancoLinha[];
  passivoPl: BalancoLinha[];
  totalAtivo: number;
  totalPassivoPl: number;
  resultadoExercicio: number;
  fecha: boolean;
}

export interface RazaoLinha {
  data: string;
  numero: number;
  historico: string;
  tipo: 'D' | 'C';
  valorCentavos: number;
  saldoAcumuladoCentavos: number;
}

export interface Razao {
  codigo: string;
  nome: string;
  de: string;
  ate: string;
  linhas: RazaoLinha[];
  saldoInicialCentavos: number;
  saldoFinalCentavos: number;
}

export interface DiarioPartida {
  codigo: string;
  nome: string;
  tipo: 'D' | 'C';
  valorCentavos: number;
}

export interface DiarioLancamento {
  numero: number;
  data: string;
  historico: string;
  totalDebitoCentavos: number;
  totalCreditoCentavos: number;
  balanceado: boolean;
  partidas: DiarioPartida[];
}

export interface Diario {
  de: string;
  ate: string;
  lancamentos: DiarioLancamento[];
}

// ── Inventário ──
export interface InventarioBase {
  de: string;
  ate: string;
  estoqueInicialCentavos: number;
  comprasCentavos: number;
  estoqueDisponivelCentavos: number;
}

export interface InventarioApuracao {
  id: number;
  de: string;
  ate: string;
  estoqueInicialCentavos: number;
  comprasCentavos: number;
  estoqueFinalCentavos: number;
  cmvCentavos: number;
  lancamentoId: number | null;
  apuradoPor: string | null;
  apuradoEm: string;
  ativo: boolean;
}

// ── Abertura ──
export interface AberturaSaldo {
  codigo: string;
  nome?: string;
  valorCentavos: number;
}

export interface Abertura {
  informada: boolean;
  dataAbertura: string | null;
  saldos: AberturaSaldo[];
}

export interface InformarAbertura {
  dataAbertura: string;
  informadoPor?: string;
  saldos: { codigo: string; valorCentavos: number }[];
}

// ── Fiscal / Simples ──
export interface FiscalConfig {
  regime: string;
  calculoAutomatico: boolean;
  anexo: string;                // I|II|III|V|AUTO
  dataInicioAtividade: string | null;
  folha12mCentavos: number;
}

export interface ApuracaoFiscal {
  automatico: boolean;
  regime: string;
  anexoConfigurado: string;
  dataInicioAtividade: string | null;
  rbt12Centavos: number;
  folha12mCentavos: number;
  fatorR: number;
  anexoEfetivo: string;
  faixa: number | null;
  aliquotaNominal: number;
  parcelaDeduzirCentavos: number;
  aliquotaEfetiva: number;
  mesesAtividade: number;
  proporcionalizado: boolean;
  excedeSimples: boolean;
}

// ── DAS / Simples (nativo do módulo) ──
export interface DasPagamento {
  id: number;
  competencia: string;
  valorCentavos: number;
  dataPagamento: string;
  contaLiquidacao: string;
  contaLiquidacaoNome: string;
  lancamentoId: number | null;
  origem: string;
  createdAt: string | null;
}

export interface DasInfo {
  saldoCentavos: number;
  competencia: string | null;
  saldoCompetenciaCentavos: number | null;
  competenciaPaga: boolean;
  historico: DasPagamento[];
}

export interface PagarDas {
  competencia: string;
  valorCentavos: number;
  contaLiquidacao: string;
  dataPagamento?: string;
}

// ── Períodos / Encerramento de exercício ──
export interface PeriodoFechado {
  id: number;
  empresaId: string | null;
  competencia: string;   // 'YYYY' (exercicio) ou 'YYYY-MM' (mensal)
  tipo: string;          // mensal | exercicio
  dataFechamento: string;
  fechadoPor: string | null;
}

export interface EncerramentoPreview {
  ano: number;
  encerrado: boolean;
  receitas: number;
  custos: number;
  despesas: number;
  resultado: number;
}

export interface EncerramentoResultado {
  ano: number;
  totalReceitas: number;
  totalDespesas: number;
  resultado: number;
  lancamentoIds: number[];
}

/**
 * Fala DIRETO com a api-contabil (localhost:8750). O JWT é injetado pelo authInterceptor.
 * Espelha o ContabilidadeService do Odonto, mas nos paths reais /v1/* (não no proxy do ERP).
 */
@Injectable({ providedIn: 'root' })
export class ContabilidadeService {
  private http = inject(HttpClient);
  private base = `${API_BASE}/v1`;

  // ── Fiscal ──
  fiscalConfig(): Observable<FiscalConfig> {
    return this.http.get<FiscalConfig>(`${this.base}/fiscal/config`);
  }

  salvarFiscalConfig(dto: FiscalConfig): Observable<FiscalConfig> {
    return this.http.put<FiscalConfig>(`${this.base}/fiscal/config`, dto);
  }

  fiscalApuracao(): Observable<ApuracaoFiscal> {
    return this.http.get<ApuracaoFiscal>(`${this.base}/fiscal/apuracao`);
  }

  // ── Abertura ──
  abertura(): Observable<Abertura> {
    return this.http.get<Abertura>(`${this.base}/abertura`);
  }

  informarAbertura(dto: InformarAbertura): Observable<Abertura> {
    return this.http.post<Abertura>(`${this.base}/abertura/informar`, dto);
  }

  // ── Contas ──
  arvoreContas(): Observable<ContaArvore[]> {
    return this.http.get<ContaArvore[]>(`${this.base}/contas/arvore`);
  }

  listarContas(): Observable<Conta[]> {
    return this.http.get<Conta[]>(`${this.base}/contas`);
  }

  criarConta(dto: ContaCreate): Observable<unknown> {
    return this.http.post(`${this.base}/contas`, dto);
  }

  atualizarConta(id: number, dto: ContaUpdate): Observable<unknown> {
    return this.http.put(`${this.base}/contas/${id}`, dto);
  }

  excluirConta(id: number): Observable<unknown> {
    return this.http.delete(`${this.base}/contas/${id}`);
  }

  // ── Pendências ──
  listarPendencias(): Observable<Pendencia[]> {
    return this.http.get<Pendencia[]>(`${this.base}/pendencias`);
  }

  salvarComoRegra(eventoId: string, regra: RegraCreate): Observable<unknown> {
    return this.http.post(`${this.base}/pendencias/${eventoId}/salvar-regra`, regra);
  }

  reprocessar(): Observable<{ total: number; reprocessados: number; aindaPendentes: number }> {
    return this.http.post<{ total: number; reprocessados: number; aindaPendentes: number }>(
      `${this.base}/pendencias/reprocessar`, {});
  }

  // ── Roteiros (regras) ──
  listarRoteiros(): Observable<Roteiro[]> {
    return this.http.get<Roteiro[]>(`${this.base}/regras`);
  }

  criarRoteiro(dto: RoteiroCreate): Observable<unknown> {
    return this.http.post(`${this.base}/regras`, dto);
  }

  excluirRoteiro(id: number): Observable<unknown> {
    return this.http.delete(`${this.base}/regras/${id}`);
  }

  // ── Inventário ──
  inventarioBase(de: string, ate: string): Observable<InventarioBase> {
    return this.http.get<InventarioBase>(`${this.base}/inventario/base`, { params: { de, ate } });
  }

  apurarCmv(de: string, ate: string, estoqueFinalCentavos: number, apuradoPor?: string): Observable<InventarioApuracao> {
    return this.http.post<InventarioApuracao>(`${this.base}/inventario/apurar`,
      { de, ate, estoqueFinalCentavos, apuradoPor });
  }

  listarInventario(): Observable<InventarioApuracao[]> {
    return this.http.get<InventarioApuracao[]>(`${this.base}/inventario`);
  }

  // ── DAS ──
  dasInfo(competencia?: string): Observable<DasInfo> {
    const params: Record<string, string> = {};
    if (competencia) params['competencia'] = competencia;
    return this.http.get<DasInfo>(`${this.base}/das`, { params });
  }

  pagarDas(dto: PagarDas): Observable<DasPagamento> {
    return this.http.post<DasPagamento>(`${this.base}/das/pagar`, dto);
  }

  // ── Períodos / Encerramento de exercício ──
  periodos(): Observable<PeriodoFechado[]> {
    return this.http.get<PeriodoFechado[]>(`${this.base}/periodos`);
  }

  encerramentoPreview(ano: number): Observable<EncerramentoPreview> {
    return this.http.get<EncerramentoPreview>(`${this.base}/periodos/encerramento/preview`, {
      params: { ano: String(ano) },
    });
  }

  encerrarExercicio(ano: number, por?: string): Observable<EncerramentoResultado> {
    return this.http.post<EncerramentoResultado>(`${this.base}/periodos/encerrar-exercicio`, { ano, por });
  }

  // ── Relatórios ──
  balancete(de?: string, ate?: string): Observable<Balancete> {
    return this.http.get<Balancete>(`${this.base}/relatorios/balancete`, { params: this.periodo(de, ate) });
  }

  dre(de?: string, ate?: string): Observable<Dre> {
    return this.http.get<Dre>(`${this.base}/relatorios/dre`, { params: this.periodo(de, ate) });
  }

  balanco(data?: string): Observable<Balanco> {
    const params: Record<string, string> = {};
    if (data) params['data'] = data;
    return this.http.get<Balanco>(`${this.base}/relatorios/balanco`, { params });
  }

  razao(conta: string, de?: string, ate?: string): Observable<Razao> {
    const params: Record<string, string> = { conta };
    if (de) params['de'] = de;
    if (ate) params['ate'] = ate;
    return this.http.get<Razao>(`${this.base}/relatorios/razao`, { params });
  }

  diario(de?: string, ate?: string): Observable<Diario> {
    return this.http.get<Diario>(`${this.base}/relatorios/diario`, { params: this.periodo(de, ate) });
  }

  private periodo(de?: string, ate?: string): Record<string, string> {
    const p: Record<string, string> = {};
    if (de) p['de'] = de;
    if (ate) p['ate'] = ate;
    return p;
  }
}
