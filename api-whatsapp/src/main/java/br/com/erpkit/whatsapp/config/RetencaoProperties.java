package br.com.erpkit.whatsapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Config da retenção operacional das conversas (LGPD item 4). Como o bot é
 * operacional-only (não trafega conteúdo clínico), o dado do módulo NÃO cai na guarda
 * de prontuário (≥20 anos, que é do ERP) — passado o prazo, o conteúdo é anonimizado.
 *
 * <p>Sobrescrito por env (relaxed binding): {@code WHATSAPP_RETENCAO_MENSAGENS_MESES},
 * {@code WHATSAPP_RETENCAO_HABILITADO}.
 */
@Component
@ConfigurationProperties(prefix = "whatsapp.retencao")
public class RetencaoProperties {

    /** Meses de retenção do conteúdo das mensagens; depois é anonimizado. Default 24. */
    private int mensagensMeses = 24;

    /** Liga/desliga o job diário de retenção (kill switch). Default true. */
    private boolean habilitado = true;

    public int getMensagensMeses() { return mensagensMeses; }
    public void setMensagensMeses(int mensagensMeses) { this.mensagensMeses = mensagensMeses; }

    public boolean isHabilitado() { return habilitado; }
    public void setHabilitado(boolean habilitado) { this.habilitado = habilitado; }
}
