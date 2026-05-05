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
    public static String normalizar(String telefone) {
        if (telefone == null) {
            return null;
        }
        // Strip todos os nao-digitos: parenteses, espacos, hifens, plus, etc.
        String digitos = telefone.replaceAll("\\D", "");
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
}
