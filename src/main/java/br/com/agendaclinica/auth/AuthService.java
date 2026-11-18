package br.com.agendaclinica.auth;

import br.com.agendaclinica.common.UnauthorizedException;
import br.com.agendaclinica.usuario.Usuario;
import br.com.agendaclinica.usuario.UsuarioRepository;
import br.com.agendaclinica.usuario.UsuarioService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    static final String MENSAGEM_PADRAO = "E-mail ou senha inválidos";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Hash de uma senha qualquer, usado quando o e-mail não existe (ver login). */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.hashFalso = passwordEncoder.encode("senha-falsa-so-para-gastar-o-mesmo-tempo");
    }

    public LoginResponse login(LoginRequest req) {
        Optional<Usuario> usuario = usuarioRepository.findByEmail(UsuarioService.normalizarEmail(req.email()));

        // Sempre conferimos um hash, mesmo quando o e-mail não existe. Se só conferíssemos para e-mails
        // existentes, a diferença de tempo de resposta revelaria quais e-mails estão cadastrados.
        String hash = usuario.map(Usuario::getSenhaHash).orElse(hashFalso);
        boolean senhaConfere = passwordEncoder.matches(req.senha(), hash);

        // Mesma mensagem para e-mail inexistente, senha errada e usuário inativo: não ajuda quem tenta adivinhar.
        if (usuario.isEmpty() || !senhaConfere || !usuario.get().isAtivo()) {
            throw new UnauthorizedException(MENSAGEM_PADRAO);
        }
        return LoginResponse.bearer(jwtService.gerarToken(usuario.get()), jwtService.validadeEmSegundos());
    }
}
