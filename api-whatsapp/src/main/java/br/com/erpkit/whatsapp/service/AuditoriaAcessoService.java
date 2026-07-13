package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.AuditoriaAcesso;
import br.com.erpkit.whatsapp.repository.AuditoriaAcessoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trilha de auditoria de acesso ao dado do paciente no inbox (LGPD item 3).
 *
 * <p>Grava QUEM acessou o dado de QUEM e QUANDO. Duas classes de acao:
 * <ul>
 *   <li>{@link #ABRIU_CHAT}: leitura do historico de um paciente. O inbox faz <b>polling</b>
 *       (~4s) do chat aberto, entao aqui ha <b>dedup</b> por {@code (atendente, telefone)} numa
 *       janela de 30min — 1 registro por abertura, nao 1 por poll. Grao certo pra LGPD
 *       ("fulano acessou o paciente X por volta de tal hora") sem inundar a trilha.</li>
 *   <li>Acoes de clique (assumir/encerrar handoff): baixa frequencia, registram sempre.</li>
 * </ul>
 *
 * <p>Best-effort: falha ao gravar auditoria <b>nunca</b> quebra a request (o interceptor
 * grava no {@code afterCompletion}, com a resposta ja enviada) — apenas loga WARN.
 */
@Service
public class AuditoriaAcessoService {

    /** Acao de leitura do chat de um paciente — a unica com dedup (o inbox faz polling). */
    public static final String ABRIU_CHAT = "abriu_chat";

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAcessoService.class);
    private static final Duration JANELA_DEDUP = Duration.ofMinutes(30);

    private final AuditoriaAcessoRepository repository;
    private final ConcurrentHashMap<String, Instant> ultimaAbertura = new ConcurrentHashMap<>();

    public AuditoriaAcessoService(AuditoriaAcessoRepository repository) {
        this.repository = repository;
    }

    /** Registra um acesso. {@code email} pode ser null (X-API-Key = ERP/sistema). */
    @Transactional
    public void registrar(String email, String acao, String telefone) {
        if (acao == null || telefone == null) {
            return;
        }
        if (ABRIU_CHAT.equals(acao) && !deveRegistrarAbertura(email, telefone)) {
            return;
        }
        try {
            repository.save(new AuditoriaAcesso(email, acao, telefone));
        } catch (RuntimeException e) {
            log.warn("Falha ao registrar auditoria (acao={} telefone={}): {}", acao, telefone, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaAcesso> listar(Pageable pageable) {
        return repository.findAllByOrderByCriadoEmDesc(pageable);
    }

    private boolean deveRegistrarAbertura(String email, String telefone) {
        String chave = (email == null ? "?" : email) + "|" + telefone;
        Instant agora = Instant.now();
        Instant ultimo = ultimaAbertura.get(chave);
        if (ultimo != null && agora.isBefore(ultimo.plus(JANELA_DEDUP))) {
            return false;
        }
        ultimaAbertura.put(chave, agora);
        return true;
    }
}
