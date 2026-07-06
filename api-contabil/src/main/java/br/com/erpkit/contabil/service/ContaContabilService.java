package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.dto.ContaArvoreNode;
import br.com.erpkit.contabil.dto.ContaCreateDTO;
import br.com.erpkit.contabil.dto.ContaUpdateDTO;
import br.com.erpkit.contabil.model.ContaContabil;
import br.com.erpkit.contabil.repository.ContaContabilRepository;
import br.com.erpkit.contabil.repository.PartidaRepository;
import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContaContabilService {

    private static final Set<String> TIPOS = Set.of("sintetica", "analitica");
    private static final Set<String> NATUREZAS = Set.of("D", "C");
    private static final Set<String> GRUPOS = Set.of("ativo", "passivo", "pl", "receita", "custo", "despesa");

    private final ContaContabilRepository repository;
    private final PartidaRepository partidaRepository;

    public ContaContabilService(ContaContabilRepository repository, PartidaRepository partidaRepository) {
        this.repository = repository;
        this.partidaRepository = partidaRepository;
    }

    public List<ContaContabil> listar() {
        return repository.findByAtivoTrueOrderByCodigo();
    }

    public ContaContabil buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ModuloException("Conta não encontrada: " + id, HttpStatus.NOT_FOUND));
    }

    public ContaContabil buscarPorCodigo(String codigo) {
        return repository.findByCodigo(codigo)
                .orElseThrow(() -> new ModuloException("Conta não encontrada: " + codigo, HttpStatus.NOT_FOUND));
    }

    /** Árvore do plano de contas, montada a partir do código hierárquico. */
    public List<ContaArvoreNode> arvore() {
        List<ContaContabil> contas = repository.findByAtivoTrueOrderByCodigo();
        Map<String, ContaArvoreNode> porCodigo = new LinkedHashMap<>();
        for (ContaContabil c : contas) {
            porCodigo.put(c.getCodigo(), ContaArvoreNode.de(c));
        }
        List<ContaArvoreNode> raizes = new ArrayList<>();
        for (ContaContabil c : contas) {
            ContaArvoreNode node = porCodigo.get(c.getCodigo());
            String paiCodigo = codigoPai(c.getCodigo());
            ContaArvoreNode pai = paiCodigo == null ? null : porCodigo.get(paiCodigo);
            if (pai != null) {
                pai.getFilhos().add(node);
            } else {
                raizes.add(node);
            }
        }
        return raizes;
    }

    @Transactional
    public ContaContabil criar(ContaCreateDTO dto) {
        validar(dto);
        if (repository.existsByCodigo(dto.getCodigo())) {
            throw new ModuloException("Já existe conta com o código " + dto.getCodigo(), HttpStatus.CONFLICT);
        }
        ContaContabil c = new ContaContabil();
        c.setCodigo(dto.getCodigo().trim());
        c.setNome(dto.getNome().trim());
        c.setTipo(dto.getTipo());
        c.setNatureza(dto.getNatureza());
        c.setGrupo(dto.getGrupo());
        c.setRetificadora(dto.isRetificadora());
        c.setNivel(nivelDoCodigo(dto.getCodigo()));
        // Só conta analítica recebe lançamento (invariante F5).
        c.setAceitaLancamento("analitica".equals(dto.getTipo()));
        c.setAtivo(true);
        String paiCodigo = codigoPai(dto.getCodigo());
        if (paiCodigo != null) {
            repository.findByCodigo(paiCodigo).ifPresent(pai -> c.setPaiId(pai.getId()));
        }
        return repository.save(c);
    }

    /** Edita a conta: só nome, natureza e retificadora. Código, tipo e grupo são imutáveis. */
    @Transactional
    public ContaContabil atualizar(Long id, ContaUpdateDTO dto) {
        ContaContabil c = buscarPorId(id);
        if (!NATUREZAS.contains(dto.getNatureza())) {
            throw new ModuloException("Natureza inválida (use D|C): " + dto.getNatureza());
        }
        c.setNome(dto.getNome().trim());
        c.setNatureza(dto.getNatureza());
        c.setRetificadora(dto.isRetificadora());
        return repository.save(c);
    }

    /** Exclusão lógica (ativo=false). Bloqueia se a conta já tem partida contabilizada. */
    @Transactional
    public void softDelete(Long id) {
        ContaContabil c = buscarPorId(id);
        if (partidaRepository.existsByContaId(id)) {
            throw new ModuloException("Conta com lançamentos não pode ser excluída", HttpStatus.CONFLICT);
        }
        c.setAtivo(false);
        repository.save(c);
    }

    /**
     * Garante que a conta pode receber partida: existe, está ativa e é analítica.
     * Usado pelo motor de lançamentos (invariante F5: só analítica lança).
     */
    public ContaContabil validarRecebeLancamento(Long contaId) {
        ContaContabil c = buscarPorId(contaId);
        if (!c.isAtivo()) {
            throw new ModuloException("Conta inativa não recebe lançamento: " + c.getCodigo());
        }
        if (!c.isAceitaLancamento() || !"analitica".equals(c.getTipo())) {
            throw new ModuloException("Conta sintética não recebe lançamento: " + c.getCodigo());
        }
        return c;
    }

    private void validar(ContaCreateDTO dto) {
        if (!TIPOS.contains(dto.getTipo())) {
            throw new ModuloException("Tipo inválido (use sintetica|analitica): " + dto.getTipo());
        }
        if (!NATUREZAS.contains(dto.getNatureza())) {
            throw new ModuloException("Natureza inválida (use D|C): " + dto.getNatureza());
        }
        if (!GRUPOS.contains(dto.getGrupo())) {
            throw new ModuloException("Grupo inválido: " + dto.getGrupo());
        }
    }

    private static String codigoPai(String codigo) {
        int i = codigo.lastIndexOf('.');
        return i < 0 ? null : codigo.substring(0, i);
    }

    private static int nivelDoCodigo(String codigo) {
        int niveis = 1;
        for (int i = 0; i < codigo.length(); i++) {
            if (codigo.charAt(i) == '.') niveis++;
        }
        return niveis;
    }
}
