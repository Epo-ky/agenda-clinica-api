package br.com.agendaclinica.usuario;

import br.com.agendaclinica.common.ConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario criar(UsuarioRequest req) {
        String email = normalizarEmail(req.email());
        if (repository.existsByEmail(email)) {
            throw new ConflictException("Já existe um usuário com este e-mail");
        }
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(req.senha()));
        usuario.setPerfil(req.perfil());
        usuario.setAtivo(true);
        return repository.save(usuario);
    }

    /** E-mail é comparado sempre em minúsculas e sem espaços, para "Ana@x.com" e "ana@x.com" serem o mesmo. */
    public static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
