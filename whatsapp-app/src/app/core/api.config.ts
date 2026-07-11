/**
 * Base da api-whatsapp. URLs RELATIVAS (mesma origem) — em produção o app é servido pelo
 * próprio jar da api-whatsapp (mesma porta), então não há host/porta fixos. Em dev, o
 * `ng serve` (4753) faz proxy de /api e /health para a api-whatsapp (9193) via proxy.conf.json.
 */
export const API_BASE = '';
