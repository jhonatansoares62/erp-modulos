package br.com.erpkit.whatsapp;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "br.com.erpkit")
@EnableConfigurationProperties(WhatsAppProperties.class)
@EnableScheduling // job diário de retenção (LGPD item 4)
public class WhatsAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsAppApplication.class, args);
    }
}
