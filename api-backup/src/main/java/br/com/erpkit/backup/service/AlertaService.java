package br.com.erpkit.backup.service;

import br.com.erpkit.backup.config.BackupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Alerta de falha de backup. No ERP in-JVM isso saia por {@code EmailModuloClient} +
 * {@code ConfiguracaoService}; no modulo o destino vem de {@code app.backup.alert.email}.
 *
 * <p>Fase 1: apenas registra o alerta em log (nivel ERROR). Durante o dual-run o proprio
 * ERP ainda envia o e-mail de falha, entao nao ha regressao. O envio real (POST na api-email)
 * entra no cutover (Fase 2+), quando o modulo assume o backup sozinho.
 */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);

    private final BackupProperties props;

    public AlertaService(BackupProperties props) {
        this.props = props;
    }

    public void alertar(String motivo) {
        String dest = props.getAlert().getEmail();
        String nome = props.getAlert().getNome();
        String alvo = (nome == null || nome.isBlank()) ? "" : " (" + nome + ")";
        if (dest == null || dest.isBlank()) {
            log.error("[ALERTA BACKUP]{} {} — sem e-mail de alerta configurado (app.backup.alert.email)", alvo, motivo);
            return;
        }
        log.error("[ALERTA BACKUP]{} {} — destino: {} (envio real de e-mail entra no cutover)", alvo, motivo, dest);
    }
}
