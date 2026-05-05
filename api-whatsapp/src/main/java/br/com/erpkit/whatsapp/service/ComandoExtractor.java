package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.util.TipoMensagem;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Extrai keyword/comando de uma mensagem entrante (D-05 do CONTEXT).
 *
 * <ul>
 *   <li><b>text:</b> primeira palavra do conteudo, lowercase. Ex: "Orcamento 1234" -&gt; "orcamento"</li>
 *   <li><b>interactive_button / interactive_list:</b> id (parser Phase 2 formata "id|title").
 *       Ex: "aprovar_42|Aprovar" -&gt; "aprovar_42"</li>
 *   <li><b>document / image / audio:</b> tipo literal — "document", "image", "audio".
 *       NOTA: {@code TipoMensagem} Phase 2 NAO inclui constant {@code VIDEO} (apenas 7
 *       constants: TEXT, INTERACTIVE_BUTTON, INTERACTIVE_LIST, DOCUMENT, IMAGE, AUDIO,
 *       DESCONHECIDO). Se um payload {@code video} chegar, sera mapeado pelo parser
 *       como {@code DESCONHECIDO} e este extractor retornara {@code null} (skip dispatch).</li>
 *   <li><b>desconhecido / null / conteudo invalido:</b> retorna {@code null} (skip dispatch ERP)</li>
 * </ul>
 *
 * <p>Logica pura sem I/O — testavel sem Spring context (instanciacao direta no test).
 *
 * <p>Por que NAO ha NLP/AI: PROJECT.md "Out of Scope" — keywords simples sao
 * suficientes pelo design. Phase 5 (lib-whatsapp-client) adiciona registry com
 * prefix matching (ex: "aprovar 1234" -&gt; handler "aprovar").
 */
@Service
public class ComandoExtractor {

    public String extrair(String tipo, String conteudo) {
        if (tipo == null) {
            return null;
        }
        return switch (tipo) {
            case TipoMensagem.TEXT -> primeiraPalavra(conteudo);
            case TipoMensagem.INTERACTIVE_BUTTON, TipoMensagem.INTERACTIVE_LIST -> idDeInteractive(conteudo);
            case TipoMensagem.DOCUMENT, TipoMensagem.IMAGE, TipoMensagem.AUDIO -> tipo;
            default -> null;  // desconhecido / outros: skip dispatch
        };
    }

    private String primeiraPalavra(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) {
            return null;
        }
        return conteudo.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    private String idDeInteractive(String conteudo) {
        // parser Phase 2 formata interactive como "id|title"
        if (conteudo == null) {
            return null;
        }
        int sep = conteudo.indexOf('|');
        return sep > 0 ? conteudo.substring(0, sep).toLowerCase(Locale.ROOT) : null;
    }
}
