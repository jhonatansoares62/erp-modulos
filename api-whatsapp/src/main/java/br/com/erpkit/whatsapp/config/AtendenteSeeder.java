package br.com.erpkit.whatsapp.config;

import br.com.erpkit.whatsapp.model.Atendente;
import br.com.erpkit.whatsapp.repository.AtendenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria um atendente admin seed no primeiro boot (tabela vazia). A senha vem de config
 * (whatsapp.auth.seed-password) e e gravada como hash BCrypt — nunca em claro.
 */
@Component
public class AtendenteSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AtendenteSeeder.class);

    private final AtendenteRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties props;

    public AtendenteSeeder(AtendenteRepository repository, PasswordEncoder passwordEncoder, AuthProperties props) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        Atendente a = new Atendente();
        // E-mail sempre em minusculas: garante a unicidade case-insensitive no nivel do
        // UNIQUE simples da coluna (V9 nao usa indice funcional lower(email), por causa do H2).
        a.setEmail(props.getSeedEmail().toLowerCase());
        a.setNome(props.getSeedNome());
        a.setSenhaHash(passwordEncoder.encode(props.getSeedPassword()));
        a.setAtivo(true);
        repository.save(a);
        log.info("Atendente seed criado: {} (troque a senha em producao via whatsapp.auth.seed-password)",
                props.getSeedEmail());
    }
}
