package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.CustoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a agregacao de {@code pricing_analytics} ({@link MetaAnalyticsClient#parseCusto})
 * com o shape real da Graph API (capturado ao vivo do WABA).
 */
class MetaAnalyticsClientTest {

    private static final Instant DE = Instant.ofEpochSecond(1783000000L);
    private static final Instant ATE = Instant.ofEpochSecond(1783800000L);

    @Test
    @DisplayName("agrega data_points reais (tudo FREE_CUSTOMER_SERVICE, custo 0)")
    void agrega_free_service() {
        String body = "{\"pricing_analytics\":{\"data\":[{\"data_points\":["
                + "{\"pricing_type\":\"FREE_CUSTOMER_SERVICE\",\"pricing_category\":\"SERVICE\",\"volume\":13,\"cost\":0},"
                + "{\"pricing_type\":\"FREE_CUSTOMER_SERVICE\",\"pricing_category\":\"SERVICE\",\"volume\":11,\"cost\":0},"
                + "{\"pricing_type\":\"FREE_CUSTOMER_SERVICE\",\"pricing_category\":\"SERVICE\",\"volume\":2,\"cost\":0},"
                + "{\"pricing_type\":\"FREE_CUSTOMER_SERVICE\",\"pricing_category\":\"UTILITY\",\"volume\":1,\"cost\":0}"
                + "]}]},\"id\":\"4509754199248154\"}";

        CustoResponse r = MetaAnalyticsClient.parseCusto(body, DE, ATE);

        assertThat(r.volumeTotal()).isEqualTo(27);
        assertThat(r.custoTotal()).isEqualByComparingTo("0");
        assertThat(r.volumeFaturavel()).isZero();
        assertThat(r.volumeGratis()).isEqualTo(27);
        assertThat(r.volumePorCategoria()).containsEntry("SERVICE", 26L).containsEntry("UTILITY", 1L);
        assertThat(r.volumePorTipo()).containsEntry("FREE_CUSTOMER_SERVICE", 27L);
    }

    @Test
    @DisplayName("REGULAR com custo entra como faturavel e soma o custo")
    void agrega_regular_com_custo() {
        String body = "{\"pricing_analytics\":{\"data\":[{\"data_points\":["
                + "{\"pricing_type\":\"REGULAR\",\"pricing_category\":\"MARKETING\",\"volume\":5,\"cost\":2.5},"
                + "{\"pricing_type\":\"FREE_CUSTOMER_SERVICE\",\"pricing_category\":\"SERVICE\",\"volume\":10,\"cost\":0}"
                + "]}]}}";

        CustoResponse r = MetaAnalyticsClient.parseCusto(body, DE, ATE);

        assertThat(r.volumeTotal()).isEqualTo(15);
        assertThat(r.custoTotal()).isEqualByComparingTo("2.5");
        assertThat(r.volumeFaturavel()).isEqualTo(5);
        assertThat(r.volumeGratis()).isEqualTo(10);
        assertThat(r.custoPorCategoria().get("MARKETING")).isEqualByComparingTo("2.5");
        assertThat(r.volumePorTipo()).containsEntry("REGULAR", 5L).containsEntry("FREE_CUSTOMER_SERVICE", 10L);
    }

    @Test
    @DisplayName("resposta sem pricing_analytics (so id): tudo zerado")
    void resposta_vazia() {
        CustoResponse r = MetaAnalyticsClient.parseCusto("{\"id\":\"4509754199248154\"}", DE, ATE);
        assertThat(r.volumeTotal()).isZero();
        assertThat(r.custoTotal()).isEqualByComparingTo("0");
        assertThat(r.volumePorCategoria()).isEmpty();
    }
}
