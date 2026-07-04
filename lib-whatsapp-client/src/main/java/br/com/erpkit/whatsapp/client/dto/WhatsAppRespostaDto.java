package br.com.erpkit.whatsapp.client.dto;

import java.util.List;

/**
 * Resposta que um {@code WhatsAppCommandHandler} devolve apos processar um comando
 * entrante. Discriminator-record com {@link Tipo} + factory methods (D-04): imutavel,
 * IDE-friendly e serializavel por Jackson sem {@code @JsonTypeInfo}.
 *
 * <p>{@code null} como retorno do handler significa "nao responder" (apenas registrar).
 *
 * <p>Use os factory methods em vez do construtor:
 * <pre>{@code
 *   WhatsAppRespostaDto.texto("Ola!");
 *   WhatsAppRespostaDto.documento(bytes, "orcamento.pdf", "application/pdf", "Seu orcamento");
 *   WhatsAppRespostaDto.botoes("Confirma?", List.of(new BotaoDto("sim", "Sim")));
 *   WhatsAppRespostaDto.lista("Escolha:", secoes);
 * }</pre>
 */
public record WhatsAppRespostaDto(
        Tipo tipo,
        String texto,
        DocumentoPayload documento,
        List<BotaoDto> botoes,
        List<SecaoDto> secoes
) {

    public enum Tipo { TEXTO, DOCUMENTO, BOTOES, LISTA }

    public record DocumentoPayload(byte[] bytes, String filename, String mimeType, String caption) {
    }

    public static WhatsAppRespostaDto texto(String texto) {
        return new WhatsAppRespostaDto(Tipo.TEXTO, texto, null, null, null);
    }

    public static WhatsAppRespostaDto documento(byte[] bytes, String filename, String mimeType, String caption) {
        return new WhatsAppRespostaDto(Tipo.DOCUMENTO, null,
                new DocumentoPayload(bytes, filename, mimeType, caption), null, null);
    }

    public static WhatsAppRespostaDto botoes(String texto, List<BotaoDto> botoes) {
        return new WhatsAppRespostaDto(Tipo.BOTOES, texto, null, List.copyOf(botoes), null);
    }

    public static WhatsAppRespostaDto lista(String texto, List<SecaoDto> secoes) {
        return new WhatsAppRespostaDto(Tipo.LISTA, texto, null, null, List.copyOf(secoes));
    }
}
