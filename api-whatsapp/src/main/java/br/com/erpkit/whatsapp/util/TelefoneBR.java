package br.com.erpkit.whatsapp.util;

import java.util.Set;

/**
 * Normalizacao de telefone brasileiro para o formato armazenado em {@code clientes_zap}
 * (D-03 do CONTEXT.md + PITFALLS C-13).
 *
 * <p><b>Regra ANATEL 2010 (resumida):</b>
 * <ul>
 *   <li>Numeros moveis adicionaram um 9o digito (numero local de 8 → 9 digitos).</li>
 *   <li>WhatsApp registrou os numeros ANTES dessa mudanca SEM o 9o digito para a maioria
 *       dos DDDs FORA de SP (11-19), RJ (21, 22, 24) e ES (27, 28).</li>
 *   <li>Enviar texto para um numero "errado" (com 9 num DDD que nao guardou 9, ou
 *       sem 9 num DDD que guardou) retorna error 131026 silenciosamente — mensagem
 *       NAO entregue, sem retry, sem feedback util do Meta.</li>
 * </ul>
 *
 * <p><b>Algoritmo:</b>
 * <ol>
 *   <li>Strip todos os nao-digitos (parenteses, espacos, hifens, plus).</li>
 *   <li>Se nao comeca com {@code "55"} ou tem comprimento fora 12-13: retorna sanitizado
 *       sem alteracao (numero nao-Brasil — preserve como veio).</li>
 *   <li>Extrai DDD = caracteres 2-3 (apos "55").</li>
 *   <li>Se DDD em {@link #DDDS_COM_NONO_DIGITO}: retorna sem mudanca (mantem 9o digito).</li>
 *   <li>Se DDD fora desse Set e o numero local tem 9 digitos comecando com 9: strip
 *       o 9 — resultado tem 8 digitos locais (formato pre-2010).</li>
 *   <li>Caso contrario (numero local ja tem 8 digitos, ou nao comeca com 9):
 *       retorna sanitizado sem mudanca.</li>
 * </ol>
 *
 * <p><b>Politica:</b> normalizar SEMPRE no INSERT em {@code clientes_zap} E em qualquer
 * lookup ({@code findByTelefone}). UNIQUE constraint funciona naturalmente porque ambos
 * caminhos passam pelo mesmo normalizador.
 *
 * <p><b>Pure utility</b> (private constructor) — testavel sem Spring.
 */
public final class TelefoneBR {

    /**
     * DDDs que mantiveram o 9o digito no WhatsApp:
     * <ul>
     *   <li>SP: 11, 12, 13, 14, 15, 16, 17, 18, 19</li>
     *   <li>RJ: 21, 22, 24</li>
     *   <li>ES: 27, 28</li>
     * </ul>
     * Todos os outros DDDs brasileiros precisam strip do 9 antes de chamar a Cloud API.
     */
    private static final Set<String> DDDS_COM_NONO_DIGITO = Set.of(
        "11", "12", "13", "14", "15", "16", "17", "18", "19",  // SP
        "21", "22", "24",                                       // RJ
        "27", "28"                                              // ES
    );

    private TelefoneBR() {
        // Pure utility — sem instancias
    }

    /**
     * Normaliza o telefone para o formato armazenado em {@code clientes_zap}.
     *
     * @param telefone numero em qualquer formato (com/sem +, paren, hifen, espaco)
     * @return numero normalizado contendo apenas digitos, ou {@code null} se input
     *         for {@code null}, ou string vazia se input nao tem nenhum digito.
     */
    /**
     * Sanitiza para apenas digitos, SEM remover o 9o digito — preserva o numero
     * EXATAMENTE como o Meta envia (o {@code wa_id}).
     *
     * <p><b>Use para o numero de RESPOSTA (outbound):</b> a Cloud API espera receber
     * de volta o mesmo {@code wa_id} que enviou no inbound. Empiricamente (teste real
     * 2026-07-04): o inbound veio {@code 5546920009012} (com 9) e responder para a
     * versao normalizada {@code 554620009012} (sem 9) falha. A regra de strip do 9
     * ({@link #normalizar}) serve apenas para matching/armazenamento interno.
     *
     * @param telefone numero em qualquer formato
     * @return apenas digitos, ou {@code null} se input for {@code null}
     */
    public static String sanitizar(String telefone) {
        if (telefone == null) {
            return null;
        }
        return telefone.replaceAll("\\D", "");
    }

    public static String normalizar(String telefone) {
        String digitos = sanitizar(telefone);
        if (digitos == null) {
            return null;
        }
        if (digitos.isEmpty()) {
            return digitos;  // string vazia se nada for digito
        }

        // Numero nao-Brasil (sem prefixo 55, ou comprimento estranho): retorna sanitizado
        // 12 digitos = 55 + DDD(2) + 8-digit local (sem 9)
        // 13 digitos = 55 + DDD(2) + 9-digit local (com 9)
        if (!digitos.startsWith("55") || digitos.length() < 12 || digitos.length() > 13) {
            return digitos;
        }

        String ddd = digitos.substring(2, 4);
        String numero = digitos.substring(4);

        if (DDDS_COM_NONO_DIGITO.contains(ddd)) {
            // SP/RJ/ES: WhatsApp tem registro com 9o digito; preservar como veio
            // (mas se vier sem 9 num desses DDDs, NAO adicionar — pode ser fixo)
            return digitos;
        }

        // Demais DDDs: strip 9o digito se presente (numero comeca com '9' E tem 9 digitos)
        if (numero.length() == 9 && numero.startsWith("9")) {
            return "55" + ddd + numero.substring(1);
        }

        // Ja sem 9o digito (8 digitos), ou numero estranho — retorna como veio
        return digitos;
    }

    /**
     * Numero para ENVIO na Cloud API: GARANTE o 9o digito de celular BR.
     *
     * <p>A regra ANATEL 2010 (em {@link #normalizar}) removia o 9 de DDDs fora de SP/RJ/ES,
     * mas na pratica a Cloud API HOJE so ENTREGA para celular BR COM o 9 — enviar sem o 9
     * retorna erro <b>131026 "Message undeliverable"</b> (empirico 2026-07-12: DDD 46 sem o 9
     * = 131026; com o 9 = read). Como o contato pode ter sido salvo/normalizado sem o 9,
     * re-inserimos aqui, no unico ponto de saida pra Meta.
     *
     * <p>{@code 55 + DDD(2) + 8 digitos} (celular sem o 9) → {@code 55 + DDD + 9 + 8 digitos}.
     * Numeros ja com 13 digitos (com 9) ou nao-BR passam sem mudanca.
     *
     * @param telefone numero em qualquer formato
     * @return numero pronto pra Cloud API (so digitos, com o 9), ou o input sanitizado se nao-BR
     */
    public static String paraEnvio(String telefone) {
        String d = sanitizar(telefone);
        if (d == null || d.isEmpty()) {
            return d;
        }
        // BR (55) com 12 digitos = 55 + DDD(2) + 8 (celular sem o 9) → insere o 9 apos o DDD
        if (d.startsWith("55") && d.length() == 12) {
            return "55" + d.substring(2, 4) + "9" + d.substring(4);
        }
        return d;
    }
}
