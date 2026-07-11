package br.com.erpkit.whatsapp.service;

import br.com.erpkit.shared.exception.ModuloException;
import br.com.erpkit.whatsapp.model.Atendente;
import br.com.erpkit.whatsapp.repository.AtendenteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Autenticacao do atendente: valida e-mail + senha (BCrypt) contra a tabela local.
 */
@Service
public class AuthService {

    private final AtendenteRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AtendenteRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public Atendente autenticar(String email, String senha) {
        return repository.findAtivoByEmail(email)
                .filter(a -> passwordEncoder.matches(senha, a.getSenhaHash()))
                .orElseThrow(() -> new ModuloException("E-mail ou senha invalidos", HttpStatus.UNAUTHORIZED));
    }

    public Atendente buscarPorEmail(String email) {
        return repository.findAtivoByEmail(email)
                .orElseThrow(() -> new ModuloException("Atendente nao encontrado", HttpStatus.UNAUTHORIZED));
    }
}
