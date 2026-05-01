package br.com.erpkit.consultas.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "app.modulos.consultas", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ConsultasProperties.class)
public class ConsultasClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConsultasClient consultasClient(ConsultasProperties props) {
        return new ConsultasClientImpl(props);
    }
}
