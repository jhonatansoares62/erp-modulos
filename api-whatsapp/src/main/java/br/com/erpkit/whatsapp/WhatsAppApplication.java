package br.com.erpkit.whatsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "br.com.erpkit")
public class WhatsAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsAppApplication.class, args);
    }
}
