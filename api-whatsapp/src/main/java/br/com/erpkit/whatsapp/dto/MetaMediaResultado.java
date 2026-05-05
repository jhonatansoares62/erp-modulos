package br.com.erpkit.whatsapp.dto;

/**
 * Resultado interno do download de media Meta — output de
 * {@code MetaMediaClient.baixar(String mediaId)} (Wave 3).
 *
 * <p>Tupla simples bytes+mime+filename. {@code MensagemAsyncListener} (Wave 5)
 * transforma {@code bytes} em base64 (via {@code java.util.Base64}) antes de incluir
 * no {@link ComandoCallbackDTO}.
 *
 * <p>Uso 100% interno — NUNCA serializado por Jackson (ergo record e seguro aqui).
 *
 * @param bytes      payload binario completo (PDF/imagem/audio)
 * @param mimeType   MIME declarado pelo Meta no metadata (step 1 da Graph API)
 * @param filename   nome original — Meta retorna no metadata para document; pode ser {@code null} para image/audio
 */
public record MetaMediaResultado(byte[] bytes, String mimeType, String filename) { }
