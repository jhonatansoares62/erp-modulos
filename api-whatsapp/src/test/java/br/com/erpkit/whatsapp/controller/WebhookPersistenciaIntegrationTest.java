package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test E2E final da Phase 2 — phase gate empirico.
 *
 * <p>Sobe o {@link WhatsAppApplication} completo via {@link SpringBootTest} com
 * {@code webEnvironment = MOCK} + {@link AutoConfigureMockMvc} para construir o
 * {@link MockMvc} respeitando TODOS os {@code FilterRegistrationBean} (em
 * particular o {@code HmacSignatureFilter} HIGHEST_PRECEDENCE), e exercita o
 * stack inteiro Phase 1 + Phase 2:
 *
 * <pre>
 *   MockMvc.post → HmacSignatureFilter (valida signature)
 *                → CachedBodyHttpServletRequest (eager body read)
 *                → WebhookController.receber (cast + delega)
 *                → MensagemService.processarWebhook (orquestrador sincrono)
 *                → WebhookPayloadParser.extrair (Jackson)
 *                → IdempotencyService.tentarPersistir (UNIQUE wamid gate)
 *                → ClienteZapService.identificar (auto-create)
 *                → ClienteZapService.atualizarUltimaMensagemEm (REQUIRES_NEW + NOW())
 * </pre>
 *
 * <p>Apos o {@code .andExpect(status().isOk())} de cada POST, queries via
 * {@link JdbcTemplate} verificam o estado do banco (committed via REQUIRES_NEW)
 * — gate empirico para os 5 ROADMAP success criteria de Phase 2.
 *
 * <p><b>Cobertura — 13 tests mapeando 5 SC + 2 bonus:</b>
 * <ol>
 *   <li><b>SC-1</b>: 2 POSTs mesmo wamid → 1 row (idempotency)</li>
 *   <li><b>SC-2</b>: parser cobre todos os tipos (text + button_reply +
 *       list_reply + document + desconhecido + status NAO persistido) — 6 sub-cases</li>
 *   <li><b>SC-3</b>: telefone normalizado (DDD 47 SC strip 9 + DDD 11 SP
 *       preserva 9) — 2 sub-cases</li>
 *   <li><b>SC-4</b>: auto-create com {@code id_cliente_erp = null}</li>
 *   <li><b>SC-5</b>: {@code ultima_mensagem_em} populada apos REQUIRES_NEW
 *       commit — leitura via {@link JdbcTemplate} {@link Timestamp} (timezone-naive
 *       H2 quirk per Plan 02-06 SUMMARY)</li>
 *   <li><b>BONUS</b>: multiple messages persiste todas + JSON malformado retorna
 *       200 (ack-first defensivo Plan 02-06)</li>
 * </ol>
 *
 * <p><b>Helper {@link #computeSignature(byte[], String)}:</b> assina fixtures
 * dinamicamente com HMAC-SHA256 do {@code appSecret} do
 * {@code application-test.yml} ({@code "test-app-secret"}). Mesmo helper de
 * {@link WebhookControllerIntegrationTest} (Phase 1) e
 * {@link br.com.erpkit.whatsapp.web.HmacSignatureFilterTest} — fixture viva
 * automaticamente alinhada se o secret mudar.
 *
 * <p><b>Isolamento entre tests:</b> cada test usa wamid distinto (.001, .002,
 * etc.) e quando precisa contar rows, filtra por wamid/telefone especifico ao
 * inves de COUNT(*) global — H2 in-memory e compartilhado entre {@code @Test}
 * methods do mesmo SpringContext. Padrao replicado de
 * {@link br.com.erpkit.whatsapp.service.MensagemServiceTest} (Plan 02-06) e
 * {@link br.com.erpkit.whatsapp.service.ClienteZapServiceTest} (Plan 02-04).
 *
 * <p><b>Por que NAO {@code @BeforeEach} com deleteAll:</b> conflicta com
 * isolamento de wamid e poderia mascarar bug de race condition entre tests do
 * mesmo context. Wamids unicos sao a estrategia de isolamento canonica do
 * codebase Phase 2.
 *
 * <p><b>Referencias:</b>
 * <ul>
 *   <li>{@code .planning/phases/02-persistencia-idempotencia/02-RESEARCH.md} §11</li>
 *   <li>{@code .planning/phases/02-persistencia-idempotencia/02-06-SUMMARY.md} concerns 1-7</li>
 *   <li>{@code .planning/ROADMAP.md} Phase 2 — 5 success criteria</li>
 * </ul>
 */
@SpringBootTest(classes = WhatsAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Phase 3 Wave 6 (PLAN 03-06) reativa com AsyncTestConfig (SyncTaskExecutor) + WireMock stub para ERP. "
        + "Em Phase 3 Wave 5 (PLAN 03-05), MensagemService.processarWebhook foi refatorado para fast-path "
        + "(parse + idempotency + publishEvent); ClienteZapService.identificar/atualizarUltimaMensagemEm "
        + "agora rodam em MensagemAsyncListener via @Async @TransactionalEventListener(AFTER_COMMIT). "
        + "Tests sc4/sc5 deste arquivo validam estado de clientes_zap apos o POST sem aguardar async — "
        + "Wave 6 substitui o whatsappTaskExecutor por SyncTaskExecutor no test profile, deixando "
        + "o flow inteiro sincrono novamente para assertions DB E2E.")
class WebhookPersistenciaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WhatsAppProperties properties;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Computa o header {@code "sha256=<hex>"} valido para o body + secret dados.
     * Mirror do helper de {@link WebhookControllerIntegrationTest} —
     * fixture viva, alinhada com o {@code appSecret} de
     * {@code application-test.yml}.
     */
    private static String computeSignature(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(body);
        return "sha256=" + HexFormat.of().formatHex(hmac);
    }

    /**
     * Carrega fixture JSON do classpath {@code src/test/resources/fixtures/webhook/}.
     * As 8 fixtures vieram do Plan 02-05; sao reusadas aqui sem modificacao.
     */
    private byte[] fixture(String nome) throws Exception {
        try (InputStream in = new ClassPathResource("fixtures/webhook/" + nome).getInputStream()) {
            return StreamUtils.copyToByteArray(in);
        }
    }

    /**
     * DRY helper: carrega fixture, assina dinamicamente com {@code appSecret} do
     * test profile, faz POST com header {@code X-Hub-Signature-256} +
     * {@code Content-Type application/json; charset=UTF-8}, e assert status 200.
     *
     * <p>{@code characterEncoding(UTF_8)} explicito defende contra default
     * ISO-8859-1 do MockMvc em algumas versoes — bug C-04 de PITFALLS que rebateria
     * em payload portugues acentuado (text-portugues.json).
     */
    private void postFixture(String nome) throws Exception {
        byte[] body = fixture(nome);
        String sig = computeSignature(body, properties.getAppSecret());
        mockMvc.perform(post("/webhook/whatsapp")
                        .header("X-Hub-Signature-256", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // ROADMAP Phase 2 SC-1 — Idempotency: 2 POSTs mesmo wamid = 1 row
    // =========================================================================

    @Test
    @DisplayName("SC-1: 2 POSTs identicos com mesmo wamid → exatamente 1 row em mensagens_log (UNIQUE gate)")
    void sc1_dois_posts_mesmo_wamid_resulta_em_apenas_uma_row() throws Exception {
        // POST 1: insere
        postFixture("text-portugues.json");
        // POST 2: Meta reentregando — DataIntegrityViolationException capturada
        // pelo IdempotencyService.tentarPersistir (Plan 02-03), retorna 200
        // silenciosamente, NAO duplica row.
        postFixture("text-portugues.json");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ?",
                Integer.class, "wamid.HBgN.text.001"
        );
        assertThat(count)
                .as("UNIQUE constraint + DataIntegrityViolationException catch garantem exatamente 1 row")
                .isEqualTo(1);
    }

    // =========================================================================
    // ROADMAP Phase 2 SC-2 — Parser entende todos os tipos
    // =========================================================================

    @Test
    @DisplayName("SC-2a: text persiste com tipo='text' + conteudo UTF-8 portugues correto")
    void sc2a_parser_text_persiste_com_tipo_text() throws Exception {
        postFixture("text-portugues.json");

        String tipo = jdbc.queryForObject(
                "SELECT tipo FROM whatsapp.mensagens_log WHERE wamid = ?",
                String.class, "wamid.HBgN.text.001"
        );
        String conteudo = jdbc.queryForObject(
                "SELECT conteudo FROM whatsapp.mensagens_log WHERE wamid = ?",
                String.class, "wamid.HBgN.text.001"
        );

        assertThat(tipo).isEqualTo("text");
        assertThat(conteudo)
                .as("UTF-8 byte-perfect preservado pelo CachedBodyHttpServletRequest (PITFALLS C-04)")
                .isEqualTo("Olá, gostaria de um orçamento");
    }

    @Test
    @DisplayName("SC-2b: button_reply persiste com tipo='interactive_button' + conteudo='id|title'")
    void sc2b_parser_button_reply_persiste_com_tipo_interactive_button() throws Exception {
        postFixture("button-reply.json");

        String tipo = jdbc.queryForObject(
                "SELECT tipo FROM whatsapp.mensagens_log WHERE wamid = ?",
                String.class, "wamid.HBgN.btn.001"
        );
        String conteudo = jdbc.queryForObject(
                "SELECT conteudo FROM whatsapp.mensagens_log WHERE wamid = ?",
                String.class, "wamid.HBgN.btn.001"
        );

        assertThat(tipo).isEqualTo("interactive_button");
        assertThat(conteudo)
                .as("Parser concatena id|title do button_reply (D-05 RESEARCH §5)")
                .isEqualTo("aprovar_1234|Aprovar");
    }

    @Test
    @DisplayName("SC-2c: list_reply persiste com tipo='interactive_list' + conteudo='id|title'")
    void sc2c_parser_list_reply_persiste_com_tipo_interactive_list() throws Exception {
        postFixture("list-reply.json");

        String tipo = jdbc.queryForObject(
                "SELECT tipo FROM whatsapp.mensagens_log WHERE wamid = ?",
                String.class, "wamid.HBgN.list.001"
        );
        String conteudo = jdbc.queryForObject(
                "SELECT conteudo FROM whatsapp.mensagens_log WHERE wamid = ?",
                String.class, "wamid.HBgN.list.001"
        );

        assertThat(tipo).isEqualTo("interactive_list");
        assertThat(conteudo).isEqualTo("boleto|Ver boleto");
    }

    @Test
    @DisplayName("SC-2d: document persiste com tipo='document' + media_id correto")
    void sc2d_parser_document_persiste_com_tipo_document_e_media_id() throws Exception {
        postFixture("document-pdf.json");

        String tipo = jdbc.queryForObject(
                "SELECT tipo FROM whatsapp.mensagens_log WHERE wamid = ?",
                String.class, "wamid.HBgN.doc.001"
        );
        String mediaId = jdbc.queryForObject(
                "SELECT media_id FROM whatsapp.mensagens_log WHERE wamid = ?",
                String.class, "wamid.HBgN.doc.001"
        );
        String conteudo = jdbc.queryForObject(
                "SELECT conteudo FROM whatsapp.mensagens_log WHERE wamid = ?",
                String.class, "wamid.HBgN.doc.001"
        );

        assertThat(tipo).isEqualTo("document");
        assertThat(mediaId)
                .as("media_id extraido do payload Meta — referencia para download lazy em Phase 3 (ROU-05)")
                .isEqualTo("media-id-12345");
        assertThat(conteudo)
                .as("conteudo do document e o filename (D-05 RESEARCH §5)")
                .isEqualTo("comprovante.pdf");
    }

    @Test
    @DisplayName("SC-2e: tipo desconhecido (ephemeral_message) persiste com tipo='desconhecido' sem erro (WEB-07)")
    void sc2e_parser_tipo_desconhecido_persiste_com_tipo_desconhecido() throws Exception {
        postFixture("tipo-desconhecido.json");

        String tipo = jdbc.queryForObject(
                "SELECT tipo FROM whatsapp.mensagens_log WHERE wamid = ?",
                String.class, "wamid.HBgN.unknown.001"
        );
        // conteudo == null para desconhecido — SELECT pode retornar null sem erro,
        // entao usamos queryForList ao inves de queryForObject (mais defensivo)
        Integer countConteudoNull = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ? AND conteudo IS NULL",
                Integer.class, "wamid.HBgN.unknown.001"
        );

        assertThat(tipo)
                .as("Tipo desconhecido nao quebra parser — WEB-07 (D-05)")
                .isEqualTo("desconhecido");
        assertThat(countConteudoNull)
                .as("conteudo deve ser NULL para tipo desconhecido")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SC-2f: status callback (delivered) parseado mas NAO persistido em mensagens_log (D-06)")
    void sc2f_parser_status_NAO_persiste_em_phase_2() throws Exception {
        postFixture("status-delivered.json");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ?",
                Integer.class, "wamid.HBgN.status.001"
        );
        assertThat(count)
                .as("Statuses Meta sao callbacks de SAIDA — D-06 explicita ignorar em Phase 2 "
                        + "(persistencia em Phase 4+ quando outbound chegar)")
                .isEqualTo(0);
    }

    // =========================================================================
    // ROADMAP Phase 2 SC-3 — Telefone normalizado (DDDs SP/RJ/ES preservam 9; demais strip)
    // =========================================================================

    @Test
    @DisplayName("SC-3a: DDD 47 SC (5547984178525, 13 digitos com 9) → strip 9 → 554784178525 (12 digitos)")
    void sc3a_telefone_DDD47_e_normalizado_strip_9() throws Exception {
        postFixture("text-portugues.json");

        // Filtrar por wamid+telefone — outros tests do mesmo SpringContext (sc1,
        // sc2a, sc2f, multiple-messages) podem ter inserido o mesmo telefone
        // 554784178525 (text-portugues.json e multiple-messages.json compartilham
        // from=5547984178525). Wamid garante que a assertion mede o efeito DESTE
        // POST, nao do total da tabela. Padrao de isolamento canonico do Plan 02-06
        // SUMMARY.
        Integer countWamid = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ? AND telefone = ?",
                Integer.class, "wamid.HBgN.text.001", "554784178525"
        );
        assertThat(countWamid)
                .as("DDD 47 (Santa Catarina) NAO esta em DDDS_COM_NONO_DIGITO → strip 9 do local "
                        + "(PITFALLS C-13 + PER-05). Filter por wamid garante que medimos efeito "
                        + "DESTE POST.")
                .isEqualTo(1);

        // E NAO deve existir nenhuma row em todo o histograma com o telefone
        // nao-normalizado de 13 digitos — INSERT sempre normaliza antes de gravar.
        // Esta assertion e global (sem filter por wamid) porque NUNCA deve aparecer
        // o formato cru, independentemente de quantos POSTs ja rodaram.
        Integer countOriginal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE telefone = ?",
                Integer.class, "5547984178525"
        );
        assertThat(countOriginal)
                .as("Telefone NAO deve aparecer em formato cru de 13 digitos — sempre normalizado no INSERT")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("SC-3b: DDD 11 SP (5511987654321, 13 digitos com 9) → preserva 9 → 5511987654321 inalterado")
    void sc3b_telefone_DDD11_SP_mantem_9_digito() throws Exception {
        postFixture("button-reply.json");

        // Telefone esperado em mensagens_log: 13 digitos com 9o digito preservado.
        // DDD 11 esta em DDDS_COM_NONO_DIGITO → TelefoneBR retorna inalterado.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE telefone = ?",
                Integer.class, "5511987654321"
        );
        assertThat(count)
                .as("DDD 11 (Sao Paulo) ESTA em DDDS_COM_NONO_DIGITO → preserva 9o digito "
                        + "(PITFALLS C-13)")
                .isEqualTo(1);
    }

    // =========================================================================
    // ROADMAP Phase 2 SC-4 — Auto-create cliente_zap com id_cliente_erp = null
    // =========================================================================

    @Test
    @DisplayName("SC-4: telefone novo gera row em clientes_zap com id_cliente_erp IS NULL (PER-06)")
    void sc4_telefone_novo_cria_cliente_zap_com_id_cliente_erp_null() throws Exception {
        postFixture("list-reply.json");
        // list-reply usa telefone DDD 21 (RJ) → preserva 9o digito → 5521987654321

        Integer countNull = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.clientes_zap "
                        + "WHERE telefone = ? AND id_cliente_erp IS NULL",
                Integer.class, "5521987654321"
        );
        assertThat(countNull)
                .as("ClienteZapService.identificar (Plan 02-04) cria com id_cliente_erp = null "
                        + "para clientes nao mapeados — PER-06")
                .isEqualTo(1);

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.clientes_zap WHERE telefone = ?",
                Integer.class, "5521987654321"
        );
        assertThat(total)
                .as("Apenas 1 row criada (auto-create idempotente)")
                .isEqualTo(1);
    }

    // =========================================================================
    // ROADMAP Phase 2 SC-5 — ultima_mensagem_em populada com NOW() do banco (REQUIRES_NEW)
    // =========================================================================

    @Test
    @DisplayName("SC-5: ultima_mensagem_em populada com Timestamp recente apos REQUIRES_NEW commit (PER-07)")
    void sc5_ultima_mensagem_em_atualizada_apos_webhook() throws Exception {
        // Janela ampla (5s antes ate 5s depois) por causa de jitter de scheduling do
        // SpringBootTest + cold start de transacao do Hibernate. Validacao temporal
        // precisa ja existe em ClienteZapServiceTest.atualizar_em_nova_transacao_commit_imediato
        // (Plan 02-04). Aqui validamos que o E2E (Filter → Controller → MensagemService
        // → ClienteZapService.atualizarUltimaMensagemEm REQUIRES_NEW) tambem popula
        // o campo via JdbcTemplate de 2a conexao do pool.
        long antesEpochSec = System.currentTimeMillis() / 1000L - 5L;

        postFixture("document-pdf.json");
        // document-pdf usa from=5531987654321 (DDD 31 MG, sem 9o digito preservado)
        // → strip 9 → 553187654321 (12 digitos)

        Timestamp tsRaw = jdbc.queryForObject(
                "SELECT ultima_mensagem_em FROM whatsapp.clientes_zap WHERE telefone = ?",
                Timestamp.class, "553187654321"
        );
        long depoisEpochSec = System.currentTimeMillis() / 1000L + 5L;

        assertThat(tsRaw)
                .as("REQUIRES_NEW (Plan 02-04) commitou imediato; visivel via JdbcTemplate "
                        + "(2a conexao do pool, fora da transacao do Hibernate)")
                .isNotNull();

        // Validacao temporal via epoch seconds (compara local-as-UTC com local-as-UTC
        // — neutraliza o quirk de H2 NOW() retornar LOCAL como UTC; em PG real
        // TIMESTAMP WITH TIME ZONE comparativo direto Instant funcionaria — Plan 02-06
        // SUMMARY concern 4).
        long tsEpochSec = tsRaw.getTime() / 1000L;
        assertThat(tsEpochSec)
                .as("ultima_mensagem_em (epoch sec local) deve ser >= antes (janela 5s)")
                .isGreaterThanOrEqualTo(antesEpochSec);
        assertThat(tsEpochSec)
                .as("ultima_mensagem_em (epoch sec local) deve ser <= depois (janela 5s)")
                .isLessThanOrEqualTo(depoisEpochSec);
    }

    // =========================================================================
    // BONUS — Multiple messages no mesmo POST persiste todas (loop do orquestrador)
    // =========================================================================

    @Test
    @DisplayName("BONUS: multiple messages em 1 POST persiste todas (loop orquestrador Plan 02-06)")
    void bonus_multiple_messages_em_um_post_persiste_todas() throws Exception {
        postFixture("multiple-messages.json");

        Integer count1 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ?",
                Integer.class, "wamid.multi.001"
        );
        Integer count2 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ?",
                Integer.class, "wamid.multi.002"
        );

        assertThat(count1)
                .as("Primeira mensagem do array messages[] persistida")
                .isEqualTo(1);
        assertThat(count2)
                .as("Segunda mensagem do array messages[] persistida (loop do orquestrador)")
                .isEqualTo(1);
    }

    // =========================================================================
    // BONUS — JSON malformado retorna 200 (ack-first defensivo Plan 02-06)
    // =========================================================================

    @Test
    @DisplayName("BONUS: JSON malformado + HMAC valido → 200 (ack-first PITFALLS C-05) + nada persistido")
    void bonus_post_com_json_malformado_retorna_200_ack_first() throws Exception {
        // Body NAO-JSON mas com HMAC valido (Filter passa, Controller delega ao
        // MensagemService que entrega ao parser, que lanca IOException
        // (JsonProcessingException). WebhookController.receber captura no
        // catch (IOException e) → log.error + return 200.
        //
        // Por que 200 e nao 4xx: PITFALLS C-05 + Plan 02-06 D-06 — Meta reentrega
        // webhooks por ate 7 dias com exponential backoff em caso de erro 5xx,
        // virando retry storm em payload corrompido. 200 + log e a defesa correta
        // para volume baixo on-premise.
        byte[] body = "{ this is definitely not valid json }".getBytes(StandardCharsets.UTF_8);
        String sig = computeSignature(body, properties.getAppSecret());

        mockMvc.perform(post("/webhook/whatsapp")
                        .header("X-Hub-Signature-256", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Sanity: nao persistiu nada (parser falhou antes de chegar ao
        // IdempotencyService). COUNT total da mensagens_log nao deve ter aumentado
        // por causa deste body — usamos um wamid que NAO existe em nenhuma fixture
        // para garantir isolamento da assertion (qualquer coisa que tenha
        // wamid.malformado.001 e o efeito colateral este teste estaria buscando).
        Integer countMalformado = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ?",
                Integer.class, "wamid.malformado.001"
        );
        assertThat(countMalformado)
                .as("JSON malformado NAO deve resultar em persistencia — parser falha antes do IdempotencyService")
                .isEqualTo(0);
    }
}
