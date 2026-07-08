/** Lookup simples (id + nome). */
export interface Lookup {
  id: number;
  nome: string;
}

/** Card KPI de topo (dashboards das telas contábeis). */
export interface CardStat {
  label: string;
  value: string | number;
  icon: string;
  color: string;
  bg: string;
  sparklineData?: number[];
  trendPct?: number;
  trendDirection?: 'up' | 'down' | 'neutral';
  trendColor?: string;
}
