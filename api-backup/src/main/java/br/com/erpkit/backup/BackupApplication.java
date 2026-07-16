package br.com.erpkit.backup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Modulo de backup plugavel. Roda como servico independente (nao dentro da JVM do ERP):
 * pg_dump -Fc + verificacao + cifra AES-256-GCM + off-site R2 (prefixo por licenca) +
 * retencao GFS + restore-test + catch-up no boot. O ALVO a dumpar (PostgreSQL do ERP +
 * lista de bancos) vem 100% por config (app.backup.*), o que torna o modulo reutilizavel
 * entre Odonto/Calhas/Mudas.
 *
 * <p>scanBasePackages = "br.com.erpkit" para captar os beans transversais do lib-shared
 * (GlobalExceptionHandler, CorsFilter), como os demais modulos.
 */
@SpringBootApplication(scanBasePackages = "br.com.erpkit")
@EnableScheduling
public class BackupApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackupApplication.class, args);
    }
}
