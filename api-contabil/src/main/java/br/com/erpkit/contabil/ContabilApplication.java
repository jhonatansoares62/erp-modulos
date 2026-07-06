package br.com.erpkit.contabil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Módulo de contabilidade gerencial (double-entry). Recebe eventos de negócio dos
 * ERPs e os traduz em lançamentos contábeis via roteiros configuráveis.
 *
 * <p>scanBasePackages = "br.com.erpkit" para captar os beans transversais do
 * lib-shared (GlobalExceptionHandler, CorsConfig), como os demais módulos.
 */
@SpringBootApplication(scanBasePackages = "br.com.erpkit")
public class ContabilApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContabilApplication.class, args);
    }
}
