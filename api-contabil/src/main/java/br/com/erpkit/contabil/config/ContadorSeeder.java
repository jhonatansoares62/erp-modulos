package br.com.erpkit.contabil.config;

import br.com.erpkit.contabil.model.Contador;
import br.com.erpkit.contabil.repository.ContadorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria um contador admin seed no primeiro boot (tabela vazia). A senha vem de config
 * (contabil.auth.seed-password) e é gravada como hash BCrypt — nunca em claro.
 */
@Component
public class ContadorSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContadorSeeder.class);

    private final ContadorRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties props;

    public ContadorSeeder(ContadorRepository repository, PasswordEncoder passwordEncoder, AuthProperties props) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        Contador c = new Contador();
        c.setEmail(props.getSeedEmail());
        c.setNome(props.getSeedNome());
        c.setSenhaHash(passwordEncoder.encode(props.getSeedPassword()));
        c.setAtivo(true);
        repository.save(c);
        log.info("Contador seed criado: {} (troque a senha em produção via contabil.auth.seed-password)",
                props.getSeedEmail());
    }
}
