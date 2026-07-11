import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api.config';

// ── Métricas: GET /api/whatsapp/relatorios/resumo (ResumoUsoResponse) ──
export interface ResumoUso {
  de: string;
  ate: string;
  total: number;
  entrada: number;
  saida: number;
  faturaveis: number;
  porTipo: Record<string, number>;
  statusSaida: Record<string, number>;
  categoriaSaida: Record<string, number>;
  porResultado: Record<string, number>;
}

// ── Métricas: GET /api/whatsapp/relatorios/custo (CustoResponse) ──
export interface Custo {
  de: string;
  ate: string;
  volumeTotal: number;
  custoTotal: number;
  volumeFaturavel: number;
  volumeGratis: number;
  volumePorCategoria: Record<string, number>;
  custoPorCategoria: Record<string, number>;
  volumePorTipo: Record<string, number>;
}

// ── Configuração: GET/PUT /api/whatsapp/config (MetaConfigResponse) ──
export interface MetaConfig {
  phoneNumberId: string;
  accessTokenConfigurado: boolean;
  appSecretConfigurado: boolean;
  verifyTokenConfigurado: boolean;
  configurado: boolean;
  atualizadoEm: string | null;
}

// ── Configuração: corpo do PUT (MetaConfigRequest) — atualização parcial ──
export interface MetaConfigUpdate {
  phoneNumberId?: string;
  accessToken?: string;
  appSecret?: string;
  verifyToken?: string;
}

// ── Testes: GET /api/whatsapp/status (StatusResponse) ──
export interface Status {
  status: string;
  circuitBreakerState: string;
  phoneNumberId: string;
}

// ── Testes: GET /monitor/feed (MonitorController.MonitorFeed) ──
export interface MonitorMensagem {
  id: number;
  direcao: string | null; // "in" | "out"
  telefone: string | null;
  tipo: string | null;
  conteudo: string | null;
  criadoEm: number | null; // epoch millis
}

export interface MonitorFeed {
  phoneNumberId: string;
  circuitBreakerState: string;
  total: number;
  mensagens: MonitorMensagem[];
}

// ── Testes: botão reply (BotaoDto) ──
export interface Botao {
  id: string;
  title: string;
}

// ── Testes: corpo do POST /monitor/enviar (MonitorController.EnviarTeste) ──
export interface EnviarTeste {
  telefone: string;
  tipo: string; // "texto" | "botoes"
  texto: string;
  botoes?: Botao[];
}

// ── Testes: resposta do POST /monitor/enviar (MonitorController.EnvioResultado) ──
export interface EnvioResultado {
  ok: boolean;
  wamid: string | null;
  codigo: string | null;
  metaErrorCode: number | null;
  mensagem: string | null;
}

// ── Testes: GET /monitor/diagnostico (MonitorController.Diagnostico) ──
export interface DiagnosticoCheck {
  ok: boolean;
  detalhe: string;
}

export interface Diagnostico {
  phoneNumberId: string;
  metaApiBaseUrl: string;
  erpCallbackUrl: string;
  apiKeyConfigurada: boolean;
  circuitBreakerState: string;
  meta: DiagnosticoCheck;
  erp: DiagnosticoCheck;
}

// ── Conversas ──
export type Direcao = 'ENTRADA' | 'SAIDA';

// Envelope de página do Spring Data (default, sem PageResponse próprio no módulo).
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}

// GET /api/whatsapp/conversas → Page<ConversaResumoResponse> (ordenado por última atividade DESC)
export interface ConversaResumo {
  telefone: string;
  nome: string | null; // sempre null hoje — usar telefone como título fallback
  ultimaMensagemPreview: string | null;
  ultimaMensagemEm: string; // ISO
  ultimaDirecao: Direcao | null;
  totalMensagens: number;
  janelaAberta: boolean;
  janelaExpiraEm: string | null; // ISO
}

// GET /api/whatsapp/conversas/{telefone}/mensagens → Page<MensagemResponse> (ordem cronológica ASC)
export interface Mensagem {
  id: number;
  direcao: Direcao;
  tipo: string;
  conteudo: string | null;
  preview: string | null;
  timestamp: string; // ISO
  status: string | null; // só SAÍDA: sent/delivered/read/failed
  resultado: string | null; // só ENTRADA
  wamid: string | null;
}

// GET /api/whatsapp/conversas/{telefone} → detalhe do contato + janela 24h (404 se não existe)
export interface ConversaDetalhe {
  telefone: string;
  nome: string | null;
  idClienteErp: number | null;
  ultimaMensagemEm: string | null; // ISO
  totalMensagens: number;
  janelaAberta: boolean;
  janelaExpiraEm: string | null; // ISO
}

// POST /api/whatsapp/enviar-texto (EnviarTextoRequest) → EnvioResponse
export interface EnviarTextoRequest {
  telefone: string; // ^\d{10,15}$ (E.164 sem +)
  texto: string; // max 4096
}

export interface EnvioResponse {
  wamid: string;
}

/**
 * Cliente HTTP único das telas Métricas/Configuração/Testes. Os paths sob
 * {@code /api/whatsapp/*} recebem o Bearer JWT automaticamente (authInterceptor);
 * já {@code /monitor/*} (feed/enviar/diagnóstico) são endpoints de dev/meta
 * públicos — hoje a tela Testes depende deles (candidato a migrar pra
 * {@code /api/whatsapp/*} numa próxima).
 */
@Injectable({ providedIn: 'root' })
export class WhatsAppApiService {
  private http = inject(HttpClient);

  // ── Métricas ──
  resumo(de?: string, ate?: string): Observable<ResumoUso> {
    return this.http.get<ResumoUso>(`${API_BASE}/api/whatsapp/relatorios/resumo`, {
      params: this.periodo(de, ate),
    });
  }

  custo(de?: string, ate?: string, granularity?: string): Observable<Custo> {
    let params = this.periodo(de, ate);
    if (granularity) {
      params = params.set('granularity', granularity);
    }
    return this.http.get<Custo>(`${API_BASE}/api/whatsapp/relatorios/custo`, { params });
  }

  // ── Configuração ──
  obterConfig(): Observable<MetaConfig> {
    return this.http.get<MetaConfig>(`${API_BASE}/api/whatsapp/config`);
  }

  salvarConfig(body: MetaConfigUpdate): Observable<MetaConfig> {
    return this.http.put<MetaConfig>(`${API_BASE}/api/whatsapp/config`, body);
  }

  // ── Testes ──
  status(): Observable<Status> {
    return this.http.get<Status>(`${API_BASE}/api/whatsapp/status`);
  }

  feed(): Observable<MonitorFeed> {
    return this.http.get<MonitorFeed>(`${API_BASE}/monitor/feed`);
  }

  enviarTeste(body: EnviarTeste): Observable<EnvioResultado> {
    return this.http.post<EnvioResultado>(`${API_BASE}/monitor/enviar`, body);
  }

  diagnostico(): Observable<Diagnostico> {
    return this.http.get<Diagnostico>(`${API_BASE}/monitor/diagnostico`);
  }

  // ── Conversas (Inbox/Chat) ──
  listarConversas(page = 0, size = 30): Observable<Page<ConversaResumo>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ConversaResumo>>(`${API_BASE}/api/whatsapp/conversas`, { params });
  }

  listarMensagens(telefone: string, page = 0, size = 30): Observable<Page<Mensagem>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Mensagem>>(
      `${API_BASE}/api/whatsapp/conversas/${encodeURIComponent(telefone)}/mensagens`,
      { params },
    );
  }

  detalheConversa(telefone: string): Observable<ConversaDetalhe> {
    return this.http.get<ConversaDetalhe>(
      `${API_BASE}/api/whatsapp/conversas/${encodeURIComponent(telefone)}`,
    );
  }

  enviarTexto(telefone: string, texto: string): Observable<EnvioResponse> {
    const body: EnviarTextoRequest = { telefone, texto };
    return this.http.post<EnvioResponse>(`${API_BASE}/api/whatsapp/enviar-texto`, body);
  }

  private periodo(de?: string, ate?: string): HttpParams {
    let params = new HttpParams();
    if (de) {
      params = params.set('de', de);
    }
    if (ate) {
      params = params.set('ate', ate);
    }
    return params;
  }
}
