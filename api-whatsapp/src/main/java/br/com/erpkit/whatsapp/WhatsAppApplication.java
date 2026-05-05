package br.com.erpkit.whatsapp;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "br.com.erpkit")
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsAppApplication.class, args);
    }
}
