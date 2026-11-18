package br.com.agendaclinica.auth;

import br.com.agendaclinica.usuario.Perfil;
import br.com.agendaclinica.usuario.UsuarioRepository;
import br.com.agendaclinica.usuario.UsuarioRequest;
import br.com.agendaclinica.usuario.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro ADMIN na subida, se ainda não existir nenhum e a senha vier da variável ADMIN_PASSWORD.
 * Sem isso ninguém conseguiria fazer o primeiro login (só um ADMIN cria usuários).
 *
 * Fica no código, e não em migration SQL, porque o hash BCrypt precisa ser calculado pelo mesmo encoder
 * do login, e porque colocar uma senha dentro de um script SQL por interpolação é arriscado
 * (uma aspa na senha quebraria o script).
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UsuarioRepository repository;
    private final UsuarioService usuarioService;
    private final String email;
    private final String senha;

    public AdminInitializer(UsuarioRepository repository, UsuarioService usuarioService,
                            @Value("${app.admin.email}") String email,
                            @Value("${app.admin.password:}") String senha) {
        this.repository = repository;
        this.usuarioService = usuarioService;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.existsByPerfil(Perfil.ADMIN)) {
            return;
        }
        if (senha == null || senha.isBlank()) {
            log.warn("Nenhum ADMIN cadastrado e ADMIN_PASSWORD não definida: ninguém conseguirá fazer login.");
            return;
        }
        if (senha.length() < 8) {
            throw new IllegalStateException("ADMIN_PASSWORD deve ter pelo menos 8 caracteres");
        }
        usuarioService.criar(new UsuarioRequest(email, senha, Perfil.ADMIN));
        log.info("Usuário ADMIN inicial criado: {}", UsuarioService.normalizarEmail(email));
    }
}
