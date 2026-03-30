package br.com.erpkit.email.config;

import java.util.Map;

public class PresetSmtp {

    private final String host;
    private final int porta;
    private final boolean tls;
    private final String instrucoes;

    private PresetSmtp(String host, int porta, boolean tls, String instrucoes) {
        this.host = host;
        this.porta = porta;
        this.tls = tls;
        this.instrucoes = instrucoes;
    }

    private static final Map<String, PresetSmtp> PRESETS = Map.ofEntries(
            // Internacionais
            Map.entry("gmail", new PresetSmtp("smtp.gmail.com", 587, true,
                    "Use App Password: Google Account > Security > 2-Step Verification > App Passwords")),
            Map.entry("outlook", new PresetSmtp("smtp.office365.com", 587, true,
                    "Use a senha da conta Microsoft ou App Password se tiver 2FA")),
            Map.entry("hotmail", new PresetSmtp("smtp.office365.com", 587, true,
                    "Mesmo servidor do Outlook")),
            Map.entry("yahoo", new PresetSmtp("smtp.mail.yahoo.com", 587, true,
                    "Use App Password: Yahoo Account > Security > Generate App Password")),
            Map.entry("icloud", new PresetSmtp("smtp.mail.me.com", 587, true,
                    "Use App Password: appleid.apple.com > Sign-In and Security > App-Specific Passwords")),
            Map.entry("zoho", new PresetSmtp("smtp.zoho.com", 587, true,
                    "Habilitar SMTP em Zoho Mail > Settings > Mail Accounts > IMAP/SMTP")),

            // Brasileiros
            Map.entry("uol", new PresetSmtp("smtps.uol.com.br", 587, true,
                    "Email @uol.com.br")),
            Map.entry("bol", new PresetSmtp("smtps.bol.com.br", 587, true,
                    "Email @bol.com.br")),
            Map.entry("locaweb", new PresetSmtp("email-ssl.com.br", 587, true,
                    "Dominio proprio hospedado na Locaweb")),
            Map.entry("hostgator", new PresetSmtp("mail.seudominio.com.br", 587, true,
                    "Substitua 'seudominio.com.br' pelo seu dominio real")),
            Map.entry("kinghost", new PresetSmtp("smtp.kinghost.net", 587, true,
                    "Dominio proprio hospedado na KingHost")),

            // Serviços de envio em massa
            Map.entry("sendgrid", new PresetSmtp("smtp.sendgrid.net", 587, true,
                    "Username: apikey / Password: sua API Key do SendGrid")),
            Map.entry("mailgun", new PresetSmtp("smtp.mailgun.org", 587, true,
                    "Username e password do dominio verificado no Mailgun")),
            Map.entry("brevo", new PresetSmtp("smtp-relay.brevo.com", 587, true,
                    "Username: email da conta / Password: SMTP Key (nao a senha da conta)")),
            Map.entry("ses", new PresetSmtp("email-smtp.us-east-1.amazonaws.com", 587, true,
                    "Credenciais SMTP do AWS SES (nao IAM). Verificar dominio/email antes")),

            // Teste
            Map.entry("mailtrap", new PresetSmtp("sandbox.smtp.mailtrap.io", 587, true,
                    "Emails caem numa caixa virtual, nao envia de verdade. Otimo pra dev/teste"))
    );

    public static PresetSmtp buscar(String nome) {
        return PRESETS.get(nome.toLowerCase().trim());
    }

    public static Map<String, PresetSmtp> todos() {
        return PRESETS;
    }

    public String getHost() {
        return host;
    }

    public int getPorta() {
        return porta;
    }

    public boolean isTls() {
        return tls;
    }

    public String getInstrucoes() {
        return instrucoes;
    }
}
