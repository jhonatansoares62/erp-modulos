package br.com.erpkit.whatsapp.util;

/**
 * Normalizacao de telefone brasileiro para o formato CANONICO armazenado em
 * {@code clientes_zap} / {@code mensagens_log} / {@code estado_conversa} e usado no
 * envio pela Cloud API (D-03 do CONTEXT.md + PITFALLS C-13).
 *
 * <p><b>Canonico = celular BR com o 9o digito (13 digitos: {@code 55 + DDD + 9 + 8}).</b>
 * Uma unica forma para armazenar, casar (janela 24h / dedup) e enviar — sem divergencia
 * entre o que grava e o que manda pro Meta.
 *
 * <p><b>Por que COM o 9 (e nao mais strip):</b> a regra ANATEL 2010 (celulares fora de
 * SP/RJ/ES nao guardaram o 9) levava a versoes antigas a REMOVER o 9. Na pratica, a Cloud
 * API HOJE so ENTREGA para celular BR COM o 9 — enviar sem o 9 retorna erro
 * <b>131026 "Message undeliverable"</b> (empirico 2026-07-12: DDD 46 sem o 9 = 131026;
 * com o 9 = read). Alem disso o Meta manda o {@code wa_id} entrante COM o 9. Portanto o 9
 * e a forma real; guardar sem o 9 gerava numero "quebrado" (12 digitos, cara de fixo) e o
 * fail no envio. Canonicalizamos SEMPRE para 13 digitos.
 *
 * <p><b>Algoritmo:</b>
 * <ol>
 *   <li>Strip todos os nao-digitos (parenteses, espacos, hifens, plus).</li>
 *   <li>Se BR ({@code 55}) com 12 digitos = {@code 55 + DDD(2) + 8} (celular sem o 9):
 *       insere o 9 apos o DDD → 13 digitos.</li>
 *   <li>Ja com 13 digitos (com o 9), nao-BR, ou comprimento estranho: retorna sanitizado
 *       sem alteracao (preserva como veio).</li>
 * </ol>
 *
 * <p><b>Politica:</b> normalizar SEMPRE no INSERT E em qualquer lookup
 * ({@code findByTelefone}, janela 24h, historico). UNIQUE constraint funciona
 * naturalmente porque ambos caminhos passam pelo mesmo normalizador.
 *
 * <p><b>Pure utility</b> (private constructor) — testavel sem Spring.
 */
public final class TelefoneBR {

    private TelefoneBR() {
        // Pure utility — sem instancias
    }

    /**
     * Sanitiza para apenas digitos, SEM garantir o 9o digito — preserva o numero
     * EXATAMENTE como o Meta envia (o {@code wa_id}).
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

    /**
     * Normaliza o telefone para a forma CANONICA (celular BR com o 9o digito).
     *
     * @param telefone numero em qualquer formato (com/sem +, paren, hifen, espaco)
     * @return numero canonico (so digitos, celular BR com o 9), {@code null} se input
     *         for {@code null}, ou string vazia se input nao tem nenhum digito.
     */
    public static String normalizar(String telefone) {
        String digitos = sanitizar(telefone);
        if (digitos == null || digitos.isEmpty()) {
            return digitos;
        }
        // BR (55) com 12 digitos = 55 + DDD(2) + 8 (celular sem o 9) → insere o 9 apos o DDD
        if (digitos.startsWith("55") && digitos.length() == 12) {
            return "55" + digitos.substring(2, 4) + "9" + digitos.substring(4);
        }
        // Ja com 13 digitos (com o 9), ou nao-BR, ou comprimento estranho: passa como veio
        return digitos;
    }
}
