package br.com.erpkit.backup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Cliente S3-compatible minimalista para o Cloudflare R2, com assinatura AWS SigV4 feita
 * a mao (sem AWS SDK, pra nao engordar o JAR). Usa o {@link HttpClient} nativo do Java.
 * Path-style, payload UNSIGNED-PAYLOAD (R2 aceita sobre HTTPS), region "auto".
 */
@Component
public class R2Client {

    private static final Logger log = LoggerFactory.getLogger(R2Client.class);

    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final String PAYLOAD_HASH = "UNSIGNED-PAYLOAD";

    private final BackupOffsiteConfig cfg;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public R2Client(BackupOffsiteConfig cfg) {
        this.cfg = cfg;
    }

    /** Sobe um arquivo. Retorna o status HTTP (2xx = ok). */
    public int putObject(String key, File arquivo, String contentType) throws IOException, InterruptedException {
        return enviar("PUT", key, HttpRequest.BodyPublishers.ofFile(arquivo.toPath()), contentType);
    }

    /** Sobe bytes (usado no teste de conexao). */
    public int putBytes(String key, byte[] corpo, String contentType) throws IOException, InterruptedException {
        return enviar("PUT", key, HttpRequest.BodyPublishers.ofByteArray(corpo), contentType);
    }

    /** Teste de conexao: sobe um objeto-sonda pequeno; true se 2xx. */
    public boolean testarConexao(String prefixo) {
        try {
            String key = prefixo + "/connection-test-" + System.currentTimeMillis() + ".txt";
            int st = putBytes(key, ("ok " + Instant.now()).getBytes(StandardCharsets.UTF_8), "text/plain");
            if (st >= 200 && st < 300) {
                return true;
            }
            log.warn("Teste de conexao R2: HTTP {}", st);
            return false;
        } catch (Exception e) {
            log.warn("Teste de conexao R2 falhou: {}", e.getMessage());
            return false;
        }
    }

    private int enviar(String metodo, String key, HttpRequest.BodyPublisher corpo, String contentType)
            throws IOException, InterruptedException {
        try {
            Instant agora = Instant.now();
            String amzDate = AMZ_DATE.format(agora);
            String dateStamp = DATE_STAMP.format(agora);
            String host = URI.create(cfg.endpoint()).getHost();
            String canonicalUri = "/" + cfg.bucket() + "/" + encodeKey(key);

            String canonicalHeaders = "host:" + host + "\n"
                    + "x-amz-content-sha256:" + PAYLOAD_HASH + "\n"
                    + "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "host;x-amz-content-sha256;x-amz-date";

            String canonicalRequest = metodo + "\n" + canonicalUri + "\n" + "" + "\n"
                    + canonicalHeaders + "\n" + signedHeaders + "\n" + PAYLOAD_HASH;

            String scope = dateStamp + "/" + cfg.region() + "/s3/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                    + hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

            byte[] signingKey = chaveDeAssinatura(cfg.secretAccessKey(), dateStamp, cfg.region(), "s3");
            String signature = hex(hmac(signingKey, stringToSign.getBytes(StandardCharsets.UTF_8)));

            String authorization = "AWS4-HMAC-SHA256 Credential=" + cfg.accessKeyId() + "/" + scope
                    + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.endpoint() + canonicalUri))
                    .method(metodo, corpo)
                    .header("x-amz-date", amzDate)
                    .header("x-amz-content-sha256", PAYLOAD_HASH)
                    .header("Authorization", authorization)
                    .timeout(Duration.ofMinutes(5));
            if (contentType != null) {
                req.header("Content-Type", contentType);
            }

            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("R2 {} {} -> HTTP {}: {}", metodo, key, resp.statusCode(),
                        resp.body() == null ? "" : resp.body().substring(0, Math.min(400, resp.body().length())));
            }
            return resp.statusCode();
        } catch (GeneralSecurityException e) {
            throw new IOException("Falha ao assinar requisicao R2: " + e.getMessage(), e);
        }
    }

    private String encodeKey(String key) {
        String[] partes = key.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(encodeSegmento(partes[i]));
        }
        return sb.toString();
    }

    private String encodeSegmento(String s) {
        StringBuilder o = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
            if (unreserved) {
                o.append(c);
            } else {
                o.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return o.toString();
    }

    private byte[] chaveDeAssinatura(String secret, String date, String region, String service)
            throws GeneralSecurityException {
        byte[] k = ("AWS4" + secret).getBytes(StandardCharsets.UTF_8);
        k = hmac(k, date.getBytes(StandardCharsets.UTF_8));
        k = hmac(k, region.getBytes(StandardCharsets.UTF_8));
        k = hmac(k, service.getBytes(StandardCharsets.UTF_8));
        k = hmac(k, "aws4_request".getBytes(StandardCharsets.UTF_8));
        return k;
    }

    private byte[] hmac(byte[] chave, byte[] dados) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return mac.doFinal(dados);
    }

    private byte[] sha256(byte[] dados) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(dados);
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
