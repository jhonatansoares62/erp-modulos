package br.com.erpkit.email.service;

import br.com.erpkit.email.dto.EmailCreateDTO;
import br.com.erpkit.email.dto.EmailResponse;
import br.com.erpkit.email.model.ContaEmail;
import br.com.erpkit.email.model.Email;
import br.com.erpkit.email.repository.EmailRepository;
import br.com.erpkit.shared.exception.ModuloException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailRepository emailRepository;
    private final ContaEmailService contaEmailService;
    private final TemplateEngine templateEngine;
    private final Map<Long, JavaMailSenderImpl> senderCache = new ConcurrentHashMap<>();

    @Value("${modulo.email.max-tentativas:3}")
    private int maxTentativas;

    public EmailService(EmailRepository emailRepository, ContaEmailService contaEmailService, TemplateEngine templateEngine) {
        this.emailRepository = emailRepository;
        this.contaEmailService = contaEmailService;
        this.templateEngine = templateEngine;
    }

    public EmailResponse criar(EmailCreateDTO dto) {
        // Valida conta antes de enfileirar
        ContaEmail conta = contaEmailService.buscarContaParaEnvio(dto.getContaId());

        Email email = new Email();
        email.setDestinatario(dto.getDestinatario());
        email.setCc(dto.getCc());
        email.setAssunto(dto.getAssunto());
        email.setHtml(dto.isHtml());
        email.setTemplate(dto.getTemplate());
        email.setContaId(conta.getId());
        email.setOrigem(dto.getOrigem());
        email.setReferenciaId(dto.getReferenciaId());
        email.setAgendadoPara(dto.getAgendadoPara());

        if (dto.getTemplate() != null && !dto.getTemplate().isBlank()) {
            String corpoRenderizado = renderizarTemplate(dto.getTemplate(), dto.getTemplateVariaveis());
            email.setCorpo(corpoRenderizado);
            email.setHtml(true);
        } else {
            email.setCorpo(dto.getCorpo());
        }

        email = emailRepository.save(email);
        log.info("Email enfileirado: id={}, para={}, conta={}", email.getId(), email.getDestinatario(), conta.getNome());
        return toResponse(email);
    }

    public EmailResponse buscar(Long id) {
        Email email = emailRepository.findById(id)
                .orElseThrow(() -> new ModuloException("Email não encontrado", HttpStatus.NOT_FOUND));
        return toResponse(email);
    }

    public Page<EmailResponse> listar(Pageable pageable) {
        return emailRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<EmailResponse> listarPorStatus(String status, Pageable pageable) {
        return emailRepository.findByStatus(status, pageable).map(this::toResponse);
    }

    public EmailResponse reenviar(Long id) {
        Email email = emailRepository.findById(id)
                .orElseThrow(() -> new ModuloException("Email não encontrado", HttpStatus.NOT_FOUND));

        if ("enviado".equals(email.getStatus())) {
            throw new ModuloException("Email já foi enviado");
        }

        email.setStatus("pendente");
        email.setTentativas(0);
        email.setErroMensagem(null);
        email.setAtualizadoEm(LocalDateTime.now());
        email = emailRepository.save(email);

        log.info("Email reenfileirado: id={}", id);
        return toResponse(email);
    }

    public EmailResponse cancelar(Long id) {
        Email email = emailRepository.findById(id)
                .orElseThrow(() -> new ModuloException("Email não encontrado", HttpStatus.NOT_FOUND));

        if ("enviado".equals(email.getStatus())) {
            throw new ModuloException("Email já foi enviado, não pode ser cancelado");
        }

        email.setStatus("cancelado");
        email.setAtualizadoEm(LocalDateTime.now());
        email = emailRepository.save(email);

        log.info("Email cancelado: id={}", id);
        return toResponse(email);
    }

    public Map<String, Long> estatisticas() {
        List<Object[]> resultado = emailRepository.contarPorStatus();
        Map<String, Long> stats = new HashMap<>();
        for (Object[] row : resultado) {
            stats.put((String) row[0], (Long) row[1]);
        }
        return stats;
    }

    @Scheduled(fixedDelayString = "${modulo.email.intervalo-fila:30000}")
    public void processarFila() {
        List<Email> pendentes = emailRepository.buscarPendentesParaEnvio(LocalDateTime.now(), maxTentativas);

        if (pendentes.isEmpty()) {
            return;
        }

        log.info("Processando fila de emails: {} pendentes", pendentes.size());

        for (Email email : pendentes) {
            try {
                ContaEmail conta = contaEmailService.buscarContaParaEnvio(email.getContaId());
                JavaMailSender sender = criarMailSender(conta);
                enviar(email, conta, sender);
                email.setStatus("enviado");
                email.setEnviadoEm(LocalDateTime.now());
                log.info("Email enviado: id={}, para={}, via={}", email.getId(), email.getDestinatario(), conta.getNome());
            } catch (Exception e) {
                email.setTentativas(email.getTentativas() + 1);
                email.setErroMensagem(e.getMessage());
                if (email.getTentativas() >= maxTentativas) {
                    email.setStatus("falha");
                    log.error("Email falhou definitivamente: id={}, erro={}", email.getId(), e.getMessage());
                } else {
                    log.warn("Tentativa {}/{} falhou para email id={}: {}", email.getTentativas(), maxTentativas, email.getId(), e.getMessage());
                }
            }
            email.setAtualizadoEm(LocalDateTime.now());
            emailRepository.save(email);
        }
    }

    public void invalidarCacheConta(Long contaId) {
        senderCache.remove(contaId);
    }

    private void enviar(Email email, ContaEmail conta, JavaMailSender sender) throws MessagingException {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(conta.getRemetente());
        helper.setTo(email.getDestinatario());
        helper.setSubject(email.getAssunto());

        if (email.getCc() != null && !email.getCc().isBlank()) {
            helper.setCc(email.getCc().split(","));
        }

        helper.setText(email.getCorpo(), email.isHtml());
        sender.send(message);
    }

    private JavaMailSender criarMailSender(ContaEmail conta) {
        return senderCache.computeIfAbsent(conta.getId(), id -> {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(conta.getHost());
            sender.setPort(conta.getPorta());
            sender.setUsername(conta.getUsername());
            sender.setPassword(conta.getPassword());

            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            if (conta.isTls()) {
                props.put("mail.smtp.starttls.enable", "true");
            }
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");

            return sender;
        });
    }

    private String renderizarTemplate(String templateName, Map<String, Object> variaveis) {
        Context context = new Context();
        if (variaveis != null) {
            variaveis.forEach(context::setVariable);
        }
        return templateEngine.process(templateName, context);
    }

    private EmailResponse toResponse(Email email) {
        EmailResponse response = new EmailResponse();
        response.setId(email.getId());
        response.setDestinatario(email.getDestinatario());
        response.setCc(email.getCc());
        response.setAssunto(email.getAssunto());
        response.setHtml(email.isHtml());
        response.setTemplate(email.getTemplate());
        response.setStatus(email.getStatus());
        response.setContaId(email.getContaId());
        response.setTentativas(email.getTentativas());
        response.setErroMensagem(email.getErroMensagem());
        response.setOrigem(email.getOrigem());
        response.setReferenciaId(email.getReferenciaId());
        response.setAgendadoPara(email.getAgendadoPara());
        response.setEnviadoEm(email.getEnviadoEm());
        response.setCriadoEm(email.getCriadoEm());
        return response;
    }
}
