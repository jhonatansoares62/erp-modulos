package br.com.erpkit.email.service;

import br.com.erpkit.email.dto.EmailCreateDTO;
import br.com.erpkit.email.dto.EmailResponse;
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
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailRepository emailRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${modulo.email.max-tentativas:3}")
    private int maxTentativas;

    @Value("${modulo.email.remetente:noreply@erp.local}")
    private String remetente;

    public EmailService(EmailRepository emailRepository, JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.emailRepository = emailRepository;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public EmailResponse criar(EmailCreateDTO dto) {
        Email email = new Email();
        email.setDestinatario(dto.getDestinatario());
        email.setCc(dto.getCc());
        email.setAssunto(dto.getAssunto());
        email.setHtml(dto.isHtml());
        email.setTemplate(dto.getTemplate());
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
        log.info("Email enfileirado: id={}, para={}, assunto={}", email.getId(), email.getDestinatario(), email.getAssunto());
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
                enviar(email);
                email.setStatus("enviado");
                email.setEnviadoEm(LocalDateTime.now());
                log.info("Email enviado: id={}, para={}", email.getId(), email.getDestinatario());
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

    private void enviar(Email email) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(remetente);
        helper.setTo(email.getDestinatario());
        helper.setSubject(email.getAssunto());

        if (email.getCc() != null && !email.getCc().isBlank()) {
            helper.setCc(email.getCc().split(","));
        }

        helper.setText(email.getCorpo(), email.isHtml());
        mailSender.send(message);
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
