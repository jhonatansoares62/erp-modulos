package br.com.erpkit.whatsapp.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuracao condicional do modulo WhatsApp. So cria beans quando
 * {@code app.modulos.whatsapp.enabled=true}. Espelha {@code ConsultasClientAutoConfiguration},
 * adicionando o {@link WhatsAppCommandRegistry} que coleta os {@link WhatsAppCommandHandler}
 * declarados pelo ERP (respeitando {@code @Order} via {@code orderedStream}).
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "app.modulos.whatsapp", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WhatsAppClient whatsAppClient(WhatsAppProperties props) {
        return new WhatsAppClientImpl(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public WhatsAppCommandRegistry whatsAppCommandRegistry(ObjectProvider<WhatsAppCommandHandler> handlers) {
        return new WhatsAppCommandRegistry(handlers.orderedStream().toList());
    }
}
