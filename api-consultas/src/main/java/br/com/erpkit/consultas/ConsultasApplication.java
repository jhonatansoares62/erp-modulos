package br.com.erpkit.consultas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = "br.com.erpkit")
@EnableCaching
public class ConsultasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultasApplication.class, args);
    }
}
