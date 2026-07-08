import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../../core/api.config';

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

export interface Conta {
  id: number;
  codigo: string;
  nome: string;
  tipo: string;
  natureza: string;
  aceitaLancamento: boolean;
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

/**
 * Fala DIRETO com a api-contabil (localhost:8750). O JWT é injetado pelo authInterceptor.
 */
@Injectable({ providedIn: 'root' })
export class ContabilService {
  private http = inject(HttpClient);
  private base = `${API_BASE}/v1/relatorios`;

  balancete(de: string, ate: string): Observable<Balancete> {
    return this.http.get<Balancete>(`${this.base}/balancete`, { params: { de, ate } });
  }

  dre(de: string, ate: string): Observable<Dre> {
    return this.http.get<Dre>(`${this.base}/dre`, { params: { de, ate } });
  }

  balanco(data: string): Observable<Balanco> {
    return this.http.get<Balanco>(`${this.base}/balanco`, { params: { data } });
  }

  razao(conta: string, de: string, ate: string): Observable<Razao> {
    return this.http.get<Razao>(`${this.base}/razao`, { params: { conta, de, ate } });
  }

  diario(de: string, ate: string): Observable<Diario> {
    return this.http.get<Diario>(`${this.base}/diario`, { params: { de, ate } });
  }

  listarContas(): Observable<Conta[]> {
    return this.http.get<Conta[]>(`${API_BASE}/v1/contas`);
  }
}
