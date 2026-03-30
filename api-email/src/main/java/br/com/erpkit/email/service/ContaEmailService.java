package br.com.erpkit.email.service;

import br.com.erpkit.email.dto.ContaEmailCreateDTO;
import br.com.erpkit.email.dto.ContaEmailResponse;
import br.com.erpkit.email.model.ContaEmail;
import br.com.erpkit.email.repository.ContaEmailRepository;
import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ContaEmailService {

    private final ContaEmailRepository contaEmailRepository;

    public ContaEmailService(ContaEmailRepository contaEmailRepository) {
        this.contaEmailRepository = contaEmailRepository;
    }

    @Transactional
    public ContaEmailResponse criar(ContaEmailCreateDTO dto) {
        ContaEmail conta = new ContaEmail();
        conta.setNome(dto.getNome());
        conta.setHost(dto.getHost());
        conta.setPorta(dto.getPorta());
        conta.setUsername(dto.getUsername());
        conta.setPassword(dto.getPassword());
        conta.setRemetente(dto.getRemetente());
        conta.setTls(dto.isTls());

        if (dto.isPadrao()) {
            removerPadraoExistente();
        }
        conta.setPadrao(dto.isPadrao());

        conta = contaEmailRepository.save(conta);
        return toResponse(conta);
    }

    @Transactional
    public ContaEmailResponse atualizar(Long id, ContaEmailCreateDTO dto) {
        ContaEmail conta = contaEmailRepository.findById(id)
                .orElseThrow(() -> new ModuloException("Conta não encontrada", HttpStatus.NOT_FOUND));

        conta.setNome(dto.getNome());
        conta.setHost(dto.getHost());
        conta.setPorta(dto.getPorta());
        conta.setUsername(dto.getUsername());
        conta.setPassword(dto.getPassword());
        conta.setRemetente(dto.getRemetente());
        conta.setTls(dto.isTls());
        conta.setAtualizadoEm(LocalDateTime.now());

        if (dto.isPadrao() && !conta.isPadrao()) {
            removerPadraoExistente();
        }
        conta.setPadrao(dto.isPadrao());

        conta = contaEmailRepository.save(conta);
        return toResponse(conta);
    }

    public ContaEmailResponse buscar(Long id) {
        ContaEmail conta = contaEmailRepository.findById(id)
                .orElseThrow(() -> new ModuloException("Conta não encontrada", HttpStatus.NOT_FOUND));
        return toResponse(conta);
    }

    public List<ContaEmailResponse> listar() {
        return contaEmailRepository.findByAtivoTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void desativar(Long id) {
        ContaEmail conta = contaEmailRepository.findById(id)
                .orElseThrow(() -> new ModuloException("Conta não encontrada", HttpStatus.NOT_FOUND));
        conta.setAtivo(false);
        conta.setPadrao(false);
        conta.setAtualizadoEm(LocalDateTime.now());
        contaEmailRepository.save(conta);
    }

    public ContaEmail buscarContaParaEnvio(Long contaId) {
        if (contaId != null) {
            ContaEmail conta = contaEmailRepository.findById(contaId)
                    .orElseThrow(() -> new ModuloException("Conta não encontrada", HttpStatus.NOT_FOUND));
            if (!conta.isAtivo()) {
                throw new ModuloException("Conta de email está desativada");
            }
            return conta;
        }
        return contaEmailRepository.findByPadraoTrueAndAtivoTrue()
                .orElseThrow(() -> new ModuloException("Nenhuma conta padrão configurada. Cadastre uma conta ou informe contaId."));
    }

    private void removerPadraoExistente() {
        contaEmailRepository.findByPadraoTrueAndAtivoTrue().ifPresent(existente -> {
            existente.setPadrao(false);
            existente.setAtualizadoEm(LocalDateTime.now());
            contaEmailRepository.save(existente);
        });
    }

    private ContaEmailResponse toResponse(ContaEmail conta) {
        ContaEmailResponse response = new ContaEmailResponse();
        response.setId(conta.getId());
        response.setNome(conta.getNome());
        response.setHost(conta.getHost());
        response.setPorta(conta.getPorta());
        response.setUsername(conta.getUsername());
        response.setRemetente(conta.getRemetente());
        response.setTls(conta.isTls());
        response.setPadrao(conta.isPadrao());
        response.setAtivo(conta.isAtivo());
        response.setCriadoEm(conta.getCriadoEm());
        return response;
    }
}
