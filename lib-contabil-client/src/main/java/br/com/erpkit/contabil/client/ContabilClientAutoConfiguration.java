package br.com.erpkit.contabil.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "app.modulos.contabil", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ContabilProperties.class)
public class ContabilClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ContabilClient contabilClient(ContabilProperties props) {
        return new ContabilClientImpl(props);
    }
}
