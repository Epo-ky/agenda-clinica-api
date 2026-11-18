package br.com.agendaclinica.auth;

import br.com.agendaclinica.common.UnauthorizedException;
import br.com.agendaclinica.config.ClockConfig;
import br.com.agendaclinica.config.SecurityConfig;
import br.com.agendaclinica.config.SecurityErrorHandlers;
import br.com.agendaclinica.consulta.ConsultaController;
import br.com.agendaclinica.consulta.ConsultaService;
import br.com.agendaclinica.paciente.PacienteController;
import br.com.agendaclinica.paciente.PacienteService;
import br.com.agendaclinica.profissional.ProfissionalController;
import br.com.agendaclinica.profissional.ProfissionalService;
import br.com.agendaclinica.usuario.Perfil;
import br.com.agendaclinica.usuario.Usuario;
import br.com.agendaclinica.usuario.UsuarioController;
import br.com.agendaclinica.usuario.UsuarioRepository;
import br.com.agendaclinica.usuario.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa a SEGURANÇA de verdade (filtro JWT, 401, 403, perfis) em cima de todos os controllers.
 * Os services são mocks: aqui o que importa é quem pode chamar o quê, não o que a rota devolve.
 */
@WebMvcTest(controllers = {
        PacienteController.class, ProfissionalController.class, ConsultaController.class,
        UsuarioController.class, AuthController.class})
@Import({SecurityConfig.class, SecurityErrorHandlers.class, JwtService.class, ClockConfig.class})
@TestPropertySource(properties = {
        "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres!!",
        "app.jwt.expiration-minutes=120"})
class SegurancaWebTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres!!";

    private static final String PACIENTE = "{\"nome\":\"Maria\",\"cpf\":\"52998224725\",\"dataNascimento\":\"1990-05-10\"}";
    private static final String PROFISSIONAL = "{\"nome\":\"Ana\",\"registroConselho\":\"CRM 1\",\"especialidade\":\"CARDIOLOGIA\"}";
    private static final String CONSULTA = "{\"pacienteId\":1,\"profissionalId\":2,\"inicio\":\"2026-11-19T09:00:00\"}";
    private static final String CANCELAMENTO = "{\"motivo\":\"Imprevisto\"}";
    private static final String USUARIO = "{\"email\":\"x@y.com\",\"senha\":\"senha-forte-123\",\"perfil\":\"RECEPCAO\"}";

    /** Todas as rotas protegidas. soAdmin = só o perfil ADMIN pode chamar. */
    record Rota(HttpMethod metodo, String caminho, String corpo, boolean soAdmin) {
    }

    static final List<Rota> ROTAS = List.of(
            new Rota(HttpMethod.GET, "/pacientes", null, false),
            new Rota(HttpMethod.GET, "/pacientes/1", null, false),
            new Rota(HttpMethod.POST, "/pacientes", PACIENTE, false),
            new Rota(HttpMethod.PUT, "/pacientes/1", PACIENTE, false),
            new Rota(HttpMethod.DELETE, "/pacientes/1", null, true),

            new Rota(HttpMethod.GET, "/profissionais", null, false),
            new Rota(HttpMethod.GET, "/profissionais/1", null, false),
            new Rota(HttpMethod.GET, "/profissionais/1/horarios-livres?data=2026-11-19", null, false),
            new Rota(HttpMethod.POST, "/profissionais", PROFISSIONAL, true),
            new Rota(HttpMethod.PUT, "/profissionais/1", PROFISSIONAL, true),
            new Rota(HttpMethod.DELETE, "/profissionais/1", null, true),

            new Rota(HttpMethod.POST, "/consultas", CONSULTA, false),
            new Rota(HttpMethod.GET, "/consultas", null, false),
            new Rota(HttpMethod.PATCH, "/consultas/1/confirmar", null, false),
            new Rota(HttpMethod.PATCH, "/consultas/1/realizar", null, false),
            new Rota(HttpMethod.PATCH, "/consultas/1/cancelar", CANCELAMENTO, false),

            new Rota(HttpMethod.POST, "/usuarios", USUARIO, true));

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwt;

    @MockitoBean
    UsuarioRepository usuarioRepository;
    @MockitoBean
    PacienteService pacienteService;
    @MockitoBean
    ProfissionalService profissionalService;
    @MockitoBean
    ConsultaService consultaService;
    @MockitoBean
    UsuarioService usuarioService;
    @MockitoBean
    AuthService authService;

    Usuario admin;
    Usuario recepcao;
    String tokenAdmin;
    String tokenRecepcao;

    private static Usuario usuario(long id, Perfil perfil, boolean ativo) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmail("u" + id + "@clinica.com");
        u.setPerfil(perfil);
        u.setAtivo(ativo);
        return u;
    }

    @BeforeEach
    void preparar() {
        admin = usuario(1, Perfil.ADMIN, true);
        recepcao = usuario(2, Perfil.RECEPCAO, true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(recepcao));
        tokenAdmin = jwt.gerarToken(admin);
        tokenRecepcao = jwt.gerarToken(recepcao);
    }

    private int chamar(Rota rota, String token) throws Exception {
        MockHttpServletRequestBuilder req = request(rota.metodo(), rota.caminho());
        if (rota.corpo() != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(rota.corpo());
        }
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    // ---------- sem autenticação: 401 ----------

    @Test
    void todasAsRotasProtegidasExigemToken() throws Exception {
        for (Rota rota : ROTAS) {
            assertEquals(401, chamar(rota, null), "sem token em " + rota.metodo() + " " + rota.caminho());
        }
    }

    @Test
    void respostaDe401TemOFormatoPadraoDeErro() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/pacientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Não autenticado"))
                .andExpect(jsonPath("$.caminho").value("/pacientes"));
    }

    @Test
    void tokenLixoEhRecusado() throws Exception {
        for (Rota rota : ROTAS) {
            assertEquals(401, chamar(rota, "isto.nao.eh-um-token"), rota.caminho());
        }
    }

    @Test
    void tokenAssinadoComOutroSegredoEhRecusado() throws Exception {
        JwtService falsario = new JwtService("segredo-do-atacante-com-mais-de-32-caracteres", 120, Clock.systemUTC());
        String tokenFalso = falsario.gerarToken(admin); // diz ser o admin, mas a assinatura não bate

        assertEquals(401, chamar(ROTAS.get(0), tokenFalso));
    }

    @Test
    void tokenExpiradoEhRecusado() throws Exception {
        Clock passado = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        String tokenAntigo = new JwtService(SEGREDO, 120, passado).gerarToken(admin);

        assertEquals(401, chamar(ROTAS.get(0), tokenAntigo));
    }

    @Test
    void esquemaDiferenteDeBearerEhIgnorado() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/pacientes").header("Authorization", "Basic YWRtaW46YWRtaW4="))
                .andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.GET, "/pacientes").header("Authorization", "Bearer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoDeUsuarioInativadoDepoisEhRecusado() throws Exception {
        recepcao.setAtivo(false); // o token continua válido por 2h, mas o filtro confere o banco

        assertEquals(401, chamar(ROTAS.get(0), tokenRecepcao));
    }

    @Test
    void tokenValidoDeUsuarioQueDeixouDeExistirEhRecusado() throws Exception {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.empty());

        assertEquals(401, chamar(ROTAS.get(0), tokenRecepcao));
    }

    // ---------- perfis: 403 ----------

    @Test
    void recepcaoSoEhBarradaNasRotasDeAdmin() throws Exception {
        for (Rota rota : ROTAS) {
            int status = chamar(rota, tokenRecepcao);
            if (rota.soAdmin()) {
                assertEquals(403, status, "RECEPCAO em " + rota.metodo() + " " + rota.caminho());
            } else {
                assertNotEquals(403, status, "RECEPCAO em " + rota.metodo() + " " + rota.caminho());
                assertNotEquals(401, status, "RECEPCAO em " + rota.metodo() + " " + rota.caminho());
            }
        }
    }

    @Test
    void adminPassaPelaSegurancaEmTodasAsRotas() throws Exception {
        for (Rota rota : ROTAS) {
            int status = chamar(rota, tokenAdmin);
            assertNotEquals(401, status, "ADMIN em " + rota.metodo() + " " + rota.caminho());
            assertNotEquals(403, status, "ADMIN em " + rota.metodo() + " " + rota.caminho());
        }
    }

    @Test
    void respostaDe403TemOFormatoPadraoDeErro() throws Exception {
        mvc.perform(request(HttpMethod.DELETE, "/pacientes/1").header("Authorization", "Bearer " + tokenRecepcao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.erro").value("Acesso negado"));
    }

    @Test
    void operacaoDeAdminNaoExecutaOServiceQuandoOPerfilEhRecepcao() throws Exception {
        chamar(new Rota(HttpMethod.POST, "/usuarios", USUARIO, true), tokenRecepcao);

        org.mockito.Mockito.verifyNoInteractions(usuarioService);
    }

    @Test
    void operadorNaoSobeDePerfilFalsificandoOTokenPorqueOPerfilVemDoBanco() throws Exception {
        // token emitido quando o usuário 4 era ADMIN...
        Usuario antigoAdmin = usuario(4, Perfil.ADMIN, true);
        String token = jwt.gerarToken(antigoAdmin);
        // ...mas hoje o banco diz que ele é RECEPCAO
        when(usuarioRepository.findById(4L)).thenReturn(Optional.of(usuario(4, Perfil.RECEPCAO, true)));

        assertEquals(403, chamar(new Rota(HttpMethod.DELETE, "/pacientes/1", null, true), token));
    }

    // ---------- caminhos felizes e rotas públicas ----------

    @Test
    void adminApagaPacienteComSucesso() throws Exception {
        assertEquals(204, chamar(new Rota(HttpMethod.DELETE, "/pacientes/1", null, true), tokenAdmin));
    }

    @Test
    void recepcaoListaPacientesComSucesso() throws Exception {
        when(pacienteService.listar(any(), any())).thenReturn(Page.empty());

        assertEquals(200, chamar(new Rota(HttpMethod.GET, "/pacientes", null, false), tokenRecepcao));
    }

    @Test
    void loginEhPublico() throws Exception {
        when(authService.login(any())).thenReturn(LoginResponse.bearer("tok", 7200));

        mvc.perform(request(HttpMethod.POST, "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"senha\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.tipo").value("Bearer"));
    }

    @Test
    void loginComCredenciaisErradasDevolve401ComMensagemPadrao() throws Exception {
        when(authService.login(any())).thenThrow(new UnauthorizedException(AuthService.MENSAGEM_PADRAO));

        mvc.perform(request(HttpMethod.POST, "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"));
    }

    @Test
    void loginSemCamposDevolve400() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
