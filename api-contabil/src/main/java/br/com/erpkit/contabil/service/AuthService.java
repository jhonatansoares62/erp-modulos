package br.com.erpkit.contabil.service;

import br.com.erpkit.contabil.model.Contador;
import br.com.erpkit.contabil.repository.ContadorRepository;
import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Autenticação do contador: valida e-mail + senha (BCrypt) contra a tabela local.
 */
@Service
public class AuthService {

    private final ContadorRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(ContadorRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public Contador autenticar(String email, String senha) {
        Contador contador = repository.findAtivoByEmail(email)
                .filter(c -> passwordEncoder.matches(senha, c.getSenhaHash()))
                .orElseThrow(() -> new ModuloException("E-mail ou senha inválidos", HttpStatus.UNAUTHORIZED));
        return contador;
    }

    public Contador buscarPorEmail(String email) {
        return repository.findAtivoByEmail(email)
                .orElseThrow(() -> new ModuloException("Contador não encontrado", HttpStatus.UNAUTHORIZED));
    }
}
