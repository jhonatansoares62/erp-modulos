package br.com.erpkit.whatsapp.aspect;

import br.com.erpkit.whatsapp.service.WindowEnforcementService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Aspect que aplica a trava 24h ANTES de qualquer chamada outbound Cloud API
 * (D-03 do CONTEXT.md + OUT-07 do REQUIREMENTS.md).
 *
 * <p><b>{@code @Order(HIGHEST_PRECEDENCE)} crucial:</b> Spring {@code @Order}
 * semantica = lower numeric value = outermost. Resilience4j Spring Boot
 * defaults: Retry order = {@code LOWEST_PRECEDENCE-3}, CircuitBreaker =
 * {@code LOWEST_PRECEDENCE-2}. {@code HIGHEST_PRECEDENCE}
 * ({@code Integer.MIN_VALUE}) garante que este aspect rode FORA do retry loop —
 * 1 check por chamada externa, nao 1 por tentativa. Sem isso, em scenario 5xx +
 * 3 retries, {@code verificarJanela} seria chamado 3x (desperdicio + race em
 * boundary 24h durante backoff exponencial 1s/2s/4s).
 *
 * <p><b>Validacao empirica:</b>
 * {@code JanelaEnforcementAspectTest.aspect_invoca_apenas_uma_vez_em_3_retries}
 * usa Mockito spy + WireMock 5xx scenario para assert
 * {@code verify(windowService, times(1)).verificarJanela(any())} — regression
 * test obrigatorio caso refactor remova {@code @Order} (Pitfall 1 RESEARCH).
 *
 * <p><b>Convencao posicional {@code args[0]}:</b> aspect le primeiro argumento
 * como {@code String telefone}. Fail-fast com {@link IllegalStateException} em
 * runtime se metodo anotado nao seguir convencao — pegado em test, nao em prod.
 *
 * <p><b>Self-call gotcha:</b> Spring AOP NAO ativa em self-call (proxy bypass).
 * 04-04 garante que todos os 4 metodos publicos de {@code WhatsAppCloudClient}
 * sao {@code @JanelaProtegida}; metodos internos chamados de dentro do mesmo
 * bean (ex: {@code uploadMedia} interno chamado de {@code enviarDocumento})
 * NAO precisam ser anotados pois o publico ja foi protegido.
 *
 * <p>Ref: github.com/resilience4j/resilience4j/issues/2383, RESEARCH §Pattern 2.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JanelaEnforcementAspect {

    private final WindowEnforcementService windowService;

    public JanelaEnforcementAspect(WindowEnforcementService windowService) {
        this.windowService = windowService;
    }

    @Around("@annotation(br.com.erpkit.whatsapp.aspect.JanelaProtegida)")
    public Object enforce(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        if (args.length == 0 || !(args[0] instanceof String telefone)) {
            throw new IllegalStateException(
                "Metodo @JanelaProtegida deve ter telefone como primeiro argumento String: "
                    + pjp.getSignature());
        }
        // Lanca JanelaConversaFechadaException (HTTP 409 + JANELA_24H_FECHADA)
        // se janela > 24h ou cliente nao registrado — propaga para Cloud API
        // NAO ser chamada (curto-circuita ANTES do Resilience4j).
        windowService.verificarJanela(telefone);
        return pjp.proceed();
    }
}
