package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.ExportacaoTitularResponse;
import br.com.erpkit.whatsapp.dto.MensagemExportadaDTO;
import br.com.erpkit.whatsapp.dto.ResultadoEsquecimento;
import br.com.erpkit.whatsapp.model.ClienteZap;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import br.com.erpkit.whatsapp.repository.EstadoConversaRepository;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import br.com.erpkit.whatsapp.util.TelefoneBR;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * DSAR — direitos do titular (LGPD item 4, Art. 18): acesso e eliminação por telefone.
 *
 * <ul>
 *   <li><b>Exportar</b> (acesso/portabilidade): histórico completo do titular, com o
 *       {@code conteudo} <b>decifrado</b> (o converter decifra no load).</li>
 *   <li><b>Esquecer</b> (eliminação): anonimiza as mensagens (conteúdo/telefone) e remove
 *       os vínculos {@code clientes_zap} + {@code estado_conversa}. Atômico (@Transactional).
 *       A trilha de auditoria NÃO é apagada (registro de accountability/segurança).</li>
 * </ul>
 *
 * <p>Como o módulo é operacional-only, a eliminação aqui não esbarra na guarda de
 * prontuário (≥20 anos, que é do ERP).
 */
@Service
public class DsarService {

    private final MensagemLogRepository mensagemRepository;
    private final ClienteZapRepository clienteRepository;
    private final EstadoConversaRepository estadoConversaRepository;

    public DsarService(MensagemLogRepository mensagemRepository,
                       ClienteZapRepository clienteRepository,
                       EstadoConversaRepository estadoConversaRepository) {
        this.mensagemRepository = mensagemRepository;
        this.clienteRepository = clienteRepository;
        this.estadoConversaRepository = estadoConversaRepository;
    }

    @Transactional(readOnly = true)
    public ExportacaoTitularResponse exportar(String telefone) {
        String tel = TelefoneBR.normalizar(telefone);
        Long idClienteErp = clienteRepository.findByTelefone(tel)
                .map(ClienteZap::getIdClienteErp)
                .orElse(null);
        List<MensagemExportadaDTO> mensagens = mensagemRepository.findByTelefoneOrderByCriadoEmAsc(tel)
                .stream()
                .map(this::paraExport)
                .toList();
        return new ExportacaoTitularResponse(tel, idClienteErp, Instant.now().toString(), mensagens);
    }

    @Transactional
    public ResultadoEsquecimento esquecer(String telefone) {
        String tel = TelefoneBR.normalizar(telefone);
        int mensagens = mensagemRepository.anonimizarPorTelefone(tel);
        int clientes = clienteRepository.deletarPorTelefone(tel);
        int estados = estadoConversaRepository.deletarPorTelefone(tel);
        return new ResultadoEsquecimento(mensagens, clientes > 0, estados > 0);
    }

    private MensagemExportadaDTO paraExport(MensagemLog m) {
        Instant ts = m.getEventoEm() != null ? m.getEventoEm() : m.getCriadoEm();
        return new MensagemExportadaDTO(
                m.getDirecao() == null ? null : m.getDirecao().name(),
                m.getTipo(),
                m.getConteudo(),
                ts == null ? null : ts.toString(),
                m.getStatus());
    }
}
