package br.com.agendaclinica.auth;

import br.com.agendaclinica.common.UnauthorizedException;
import br.com.agendaclinica.usuario.Perfil;
import br.com.agendaclinica.usuario.Usuario;
import br.com.agendaclinica.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UsuarioRepository repository;

    // Objetos reais: o que importa aqui é o BCrypt e o JWT funcionando juntos com a regra de login.
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    JwtService jwtService = new JwtService("segredo-de-teste-com-mais-de-32-caracteres!!", 120, Clock.systemUTC());

    AuthService service;
    Usuario ana;

    @BeforeEach
    void preparar() {
        service = new AuthService(repository, encoder, jwtService);
        ana = new Usuario();
        ana.setId(5L);
        ana.setEmail("ana@clinica.com");
        ana.setSenhaHash(encoder.encode("senha-da-ana"));
        ana.setPerfil(Perfil.RECEPCAO);
        ana.setAtivo(true);
    }

    @Test
    void loginCorretoDevolveTokenValidoParaOUsuario() {
        when(repository.findByEmail("ana@clinica.com")).thenReturn(Optional.of(ana));

        LoginResponse r = service.login(new LoginRequest("ana@clinica.com", "senha-da-ana"));

        assertEquals("Bearer", r.tipo());
        assertEquals(7200L, r.expiraEmSegundos());
        assertEquals(5L, jwtService.extrairUsuarioId(r.token()).orElseThrow());
    }

    @Test
    void emailEhComparadoSemDiferenciarMaiusculasEEspacos() {
        when(repository.findByEmail("ana@clinica.com")).thenReturn(Optional.of(ana));

        LoginResponse r = service.login(new LoginRequest("  ANA@Clinica.com ", "senha-da-ana"));

        assertEquals(5L, jwtService.extrairUsuarioId(r.token()).orElseThrow());
    }

    @Test
    void senhaErradaEhRecusada() {
        when(repository.findByEmail("ana@clinica.com")).thenReturn(Optional.of(ana));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> service.login(new LoginRequest("ana@clinica.com", "senha-errada")));

        assertEquals(AuthService.MENSAGEM_PADRAO, ex.getMessage());
    }

    @Test
    void emailInexistenteDaExatamenteAMesmaMensagemDaSenhaErrada() {
        when(repository.findByEmail("fantasma@clinica.com")).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> service.login(new LoginRequest("fantasma@clinica.com", "qualquer")));

        assertEquals(AuthService.MENSAGEM_PADRAO, ex.getMessage());
    }

    @Test
    void usuarioInativoNaoLogaMesmoComSenhaCerta() {
        ana.setAtivo(false);
        when(repository.findByEmail("ana@clinica.com")).thenReturn(Optional.of(ana));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> service.login(new LoginRequest("ana@clinica.com", "senha-da-ana")));

        assertEquals(AuthService.MENSAGEM_PADRAO, ex.getMessage());
    }
}
