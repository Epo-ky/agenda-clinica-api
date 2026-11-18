package br.com.agendaclinica.auth;

import br.com.agendaclinica.usuario.Usuario;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Gera e valida tokens JWT (assinados com HS256). Stateless: o servidor não guarda sessão,
 * só confere a assinatura e a validade do token a cada requisição.
 */
@Service
public class JwtService {

    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    private final SecretKey chave;
    private final Duration validade;
    private final Clock clock;

    public JwtService(@Value("${app.jwt.secret}") String segredo,
                      @Value("${app.jwt.expiration-minutes:120}") long minutosDeValidade,
                      Clock clock) {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException(
                    "JWT_SECRET deve ter pelo menos " + TAMANHO_MINIMO_SEGREDO + " caracteres");
        }
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.validade = Duration.ofMinutes(minutosDeValidade);
        this.clock = clock;
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .claim("perfil", usuario.getPerfil().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(chave)
                .compact();
    }

    public long validadeEmSegundos() {
        return validade.toSeconds();
    }

    /**
     * Devolve o id do usuário se o token for válido (assinatura correta e não expirado);
     * vazio em qualquer outro caso (adulterado, expirado, mal formado).
     */
    public Optional<Long> extrairUsuarioId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(chave)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Long.parseLong(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
