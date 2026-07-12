package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.AssistentePersonaDTO;
import br.com.erpkit.whatsapp.dto.AssistenteRequest;
import br.com.erpkit.whatsapp.dto.AssistenteResponse;
import br.com.erpkit.whatsapp.model.ConfigAssistente;
import br.com.erpkit.whatsapp.repository.ConfigAssistenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Persona do assistente virtual (GENERICA, reusavel entre ERPs). Fonte de verdade da
 * linha singleton {@code whatsapp.config_assistente} (V11).
 *
 * <p>Ao contrario do {@link MetaConfigService}, NAO sobrepoe nenhum bean de
 * properties: a persona e lida sob demanda — pelo bloco "assistente" do callback ao
 * ERP e pelas mensagens genericas que o modulo dispara sozinho ("nao entendi" /
 * "fora do horario"). {@link #atual()} sempre devolve algo: se a linha nao existir
 * (banco sem o seed), cai nos defaults em memoria.
 */
@Service
public class AssistenteService {

    public static final int SINGLETON_ID = 1;

    private static final Logger log = LoggerFactory.getLogger(AssistenteService.class);

    private final ConfigAssistenteRepository repository;

    public AssistenteService(ConfigAssistenteRepository repository) {
        this.repository = repository;
    }

    /** Persona efetiva. Nunca nula — defaults em memoria quando a linha nao existe. */
    @Transactional(readOnly = true)
    public AssistenteResponse atual() {
        ConfigAssistente c = repository.findById(SINGLETON_ID).orElseGet(AssistenteService::padrao);
        return montarResposta(c);
    }

    /** Substitui a persona (upsert id=1) e devolve a versao efetiva. */
    @Transactional
    public AssistenteResponse atualizar(AssistenteRequest req) {
        ConfigAssistente c = repository.findById(SINGLETON_ID).orElseGet(() -> {
            ConfigAssistente novo = new ConfigAssistente();
            novo.setId(SINGLETON_ID);
            return novo;
        });

        c.setNome(trimOuNulo(req.nome()));
        c.setEmoji(trimOuNulo(req.emoji()));
        c.setTom(req.tom() == null || req.tom().isBlank() ? "informal" : req.tom().trim());
        c.setSaudacao(trimOuNulo(req.saudacao()));
        c.setMensagemNaoEntendi(trimOuNulo(req.mensagemNaoEntendi()));
        c.setMensagemForaHorario(trimOuNulo(req.mensagemForaHorario()));
        c.setHorarioInicio(trimOuNulo(req.horarioInicio()));
        c.setHorarioFim(trimOuNulo(req.horarioFim()));
        c.setDiasAtendimento(trimOuNulo(req.diasAtendimento()));
        c.setAtualizadoEm(Instant.now());

        repository.save(c);
        log.info("Persona do assistente atualizada: nome={}, tom={}", c.getNome(), c.getTom());
        return montarResposta(c);
    }

    /**
     * Persona enxuta para o bloco {@code assistente} do callback ao ERP (nome/emoji/
     * tom/saudacao). Nunca nula — defaults em memoria quando a linha nao existe.
     */
    @Transactional(readOnly = true)
    public AssistentePersonaDTO personaParaCallback() {
        ConfigAssistente c = repository.findById(SINGLETON_ID).orElseGet(AssistenteService::padrao);
        return new AssistentePersonaDTO(c.getNome(), c.getEmoji(), c.getTom(), c.getSaudacao());
    }

    private AssistenteResponse montarResposta(ConfigAssistente c) {
        return new AssistenteResponse(
                c.getNome(),
                c.getEmoji(),
                c.getTom(),
                c.getSaudacao(),
                c.getMensagemNaoEntendi(),
                c.getMensagemForaHorario(),
                c.getHorarioInicio(),
                c.getHorarioFim(),
                c.getDiasAtendimento(),
                c.getAtualizadoEm() == null ? null : c.getAtualizadoEm().toString());
    }

    /** Defaults genericos (espelham o seed da V11) para quando a linha nao existe. */
    private static ConfigAssistente padrao() {
        ConfigAssistente c = new ConfigAssistente();
        c.setId(SINGLETON_ID);
        c.setNome("Assistente");
        c.setEmoji("");
        c.setTom("informal");
        c.setSaudacao("Olá! Como posso ajudar você hoje?");
        c.setMensagemNaoEntendi("Desculpe, não entendi. Vou te mostrar as opções pra facilitar.");
        c.setMensagemForaHorario("No momento estamos fora do horário de atendimento. Assim que possível retornaremos por aqui.");
        c.setHorarioInicio("08:00");
        c.setHorarioFim("18:00");
        c.setDiasAtendimento("1,2,3,4,5");
        return c;
    }

    private static String trimOuNulo(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
