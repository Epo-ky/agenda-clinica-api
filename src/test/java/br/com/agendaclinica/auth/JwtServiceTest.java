package br.com.agendaclinica.auth;

import br.com.agendaclinica.usuario.Perfil;
import br.com.agendaclinica.usuario.Usuario;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres!!";
    private static final Instant AGORA = Instant.parse("2026-11-18T13:00:00Z");

    private static Clock relogioEm(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }

    private static JwtService servico(String segredo, Instant agora) {
        return new JwtService(segredo, 120, relogioEm(agora));
    }

    private static Usuario usuario(long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmail("ana@clinica.com");
        u.setPerfil(Perfil.RECEPCAO);
        return u;
    }

    @Test
    void tokenGeradoEValidoEDevolveOIdDoUsuario() {
        JwtService jwt = servico(SEGREDO, AGORA);

        String token = jwt.gerarToken(usuario(7));

        assertEquals(7L, jwt.extrairUsuarioId(token).orElseThrow());
    }

    @Test
    void tokenValeAteExatamenteAntesDeExpirar() {
        String token = servico(SEGREDO, AGORA).gerarToken(usuario(7));

        JwtService umMinutoAntes = servico(SEGREDO, AGORA.plus(Duration.ofMinutes(119)));

        assertTrue(umMinutoAntes.extrairUsuarioId(token).isPresent());
    }

    @Test
    void tokenExpiraAposDuasHoras() {
        String token = servico(SEGREDO, AGORA).gerarToken(usuario(7));

        JwtService depoisDeExpirar = servico(SEGREDO, AGORA.plus(Duration.ofMinutes(121)));

        assertTrue(depoisDeExpirar.extrairUsuarioId(token).isEmpty());
    }

    @Test
    void tokenAssinadoComOutroSegredoEhRecusado() {
        String tokenDeOutroSistema = servico("outro-segredo-totalmente-diferente-123456", AGORA)
                .gerarToken(usuario(7));

        assertTrue(servico(SEGREDO, AGORA).extrairUsuarioId(tokenDeOutroSistema).isEmpty());
    }

    @Test
    void tokenAdulteradoEhRecusado() {
        JwtService jwt = servico(SEGREDO, AGORA);
        String token = jwt.gerarToken(usuario(7));
        String[] partes = token.split("\\.");
        // troca o conteúdo (payload) mantendo a assinatura antiga
        String payloadFalso = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"perfil\":\"ADMIN\"}".getBytes());

        String adulterado = partes[0] + "." + payloadFalso + "." + partes[2];

        assertTrue(jwt.extrairUsuarioId(adulterado).isEmpty());
    }

    @Test
    void lixoEhRecusadoSemLancarExcecao() {
        JwtService jwt = servico(SEGREDO, AGORA);

        assertTrue(jwt.extrairUsuarioId("isso-nao-e-um-token").isEmpty());
        assertTrue(jwt.extrairUsuarioId("").isEmpty());
        assertTrue(jwt.extrairUsuarioId("a.b.c").isEmpty());
    }

    @Test
    void segredoCurtoImpedeASubida() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> servico("curto", AGORA));

        assertTrue(ex.getMessage().contains("32"));
    }

    @Test
    void segredoNuloImpedeASubida() {
        assertThrows(IllegalStateException.class, () -> servico(null, AGORA));
    }

    @Test
    void informaAValidadeEmSegundos() {
        assertEquals(7200L, servico(SEGREDO, AGORA).validadeEmSegundos());
    }
}
