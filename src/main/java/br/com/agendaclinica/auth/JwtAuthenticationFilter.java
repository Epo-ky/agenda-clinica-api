package br.com.agendaclinica.auth;

import br.com.agendaclinica.usuario.Usuario;
import br.com.agendaclinica.usuario.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Roda a cada requisição: lê "Authorization: Bearer ...", valida o token e, se estiver ok,
 * diz ao Spring Security quem é o usuário. Se não houver token válido, não autentica ninguém
 * e as regras do SecurityConfig respondem 401.
 *
 * NÃO é um @Component de propósito: um filtro registrado como bean seria incluído também na cadeia
 * do servlet, fora do Spring Security. Ele é criado dentro do SecurityConfig.
 *
 * O usuário é buscado no banco a cada requisição. Assim, quem foi inativado perde o acesso na hora
 * (não só quando o token expira) e a mudança de perfil vale imediatamente.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIXO)) {
            String token = header.substring(PREFIXO.length()).trim();
            jwtService.extrairUsuarioId(token)
                    .flatMap(usuarioRepository::findById)
                    .filter(Usuario::isAtivo)
                    .ifPresent(this::autenticar);
        }
        chain.doFilter(request, response);
    }

    private void autenticar(Usuario usuario) {
        var autoridade = new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name());
        var autenticacao = new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of(autoridade));
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
    }
}
