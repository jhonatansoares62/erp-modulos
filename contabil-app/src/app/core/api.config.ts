/**
 * Base da api-contabil. URLs RELATIVAS (mesma origem) — em produção o app é servido pelo
 * próprio jar da api-contabil (mesma porta), então não há host/porta fixos. Em dev, o
 * `ng serve` (4750) faz proxy de /v1 e /health para a api-contabil (8750) via proxy.conf.json.
 */
export const API_BASE = '';
