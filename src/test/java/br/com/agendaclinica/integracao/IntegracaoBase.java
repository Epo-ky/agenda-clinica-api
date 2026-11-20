package br.com.agendaclinica.integracao;

import br.com.agendaclinica.usuario.Perfil;
import br.com.agendaclinica.usuario.Usuario;
import br.com.agendaclinica.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Sobe a aplicação INTEIRA (controllers, segurança, services, JPA, Flyway) contra um PostgreSQL de verdade,
 * diferente dos testes unitários, que usam mocks. Cada teste começa com o banco vazio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegracaoBase {

    static final String SENHA_ADMIN = "senha-do-admin-123";
    static final String SENHA_RECEPCAO = "senha-da-recepcao-123";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UsuarioRepository usuarioRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void limparBanco() {
        // TRAVA DE SEGURANÇA: apagar tudo só é aceitável em banco de teste.
        String banco = jdbc.queryForObject("select current_database()", String.class);
        if (banco == null || !banco.endsWith("_test")) {
            throw new IllegalStateException("Recusando limpar o banco '" + banco
                    + "': testes de integração só rodam em banco cujo nome termina com _test");
        }
        jdbc.execute("TRUNCATE consultas, pacientes, profissionais, usuarios RESTART IDENTITY CASCADE");
    }

    // ---------- usuários e login ----------

    protected void criarUsuario(String email, String senha, Perfil perfil) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setSenhaHash(passwordEncoder.encode(senha));
        u.setPerfil(perfil);
        u.setAtivo(true);
        usuarioRepository.save(u);
    }

    /** Faz o login de verdade, pela API, e devolve o token. */
    protected String login(String email, String senha) throws Exception {
        String corpo = "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
        String json = mvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    protected String tokenAdmin() throws Exception {
        criarUsuario("admin@teste.com", SENHA_ADMIN, Perfil.ADMIN);
        return login("admin@teste.com", SENHA_ADMIN);
    }

    protected String tokenRecepcao() throws Exception {
        criarUsuario("recepcao@teste.com", SENHA_RECEPCAO, Perfil.RECEPCAO);
        return login("recepcao@teste.com", SENHA_RECEPCAO);
    }

    // ---------- chamadas ----------

    protected ResultActions enviar(MockHttpServletRequestBuilder req, String token, String corpo) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mvc.perform(req);
    }

    protected ResultActions post(String url, String token, String corpo) throws Exception {
        return enviar(MockMvcRequestBuilders.post(url), token, corpo);
    }

    protected ResultActions get(String url, String token) throws Exception {
        return enviar(MockMvcRequestBuilders.get(url), token, null);
    }

    protected ResultActions patch(String url, String token, String corpo) throws Exception {
        return enviar(MockMvcRequestBuilders.patch(url), token, corpo);
    }

    protected ResultActions delete(String url, String token) throws Exception {
        return enviar(MockMvcRequestBuilders.delete(url), token, null);
    }

    // ---------- dados de apoio ----------

    /** Cadastra pelo endpoint real e devolve o id. */
    protected long criarPaciente(String token, String nome, String cpf) throws Exception {
        String corpo = "{\"nome\":\"" + nome + "\",\"cpf\":\"" + cpf + "\",\"dataNascimento\":\"1990-05-10\"}";
        String json = post("/pacientes", token, corpo).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    protected long criarProfissional(String token, String nome, int duracaoMin) throws Exception {
        String corpo = "{\"nome\":\"" + nome + "\",\"registroConselho\":\"CRM-" + nome.hashCode()
                + "\",\"especialidade\":\"CARDIOLOGIA\",\"duracaoConsultaMin\":" + duracaoMin + "}";
        String json = post("/profissionais", token, corpo).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    protected static String corpoConsulta(long pacienteId, long profissionalId, String inicio) {
        return "{\"pacienteId\":" + pacienteId + ",\"profissionalId\":" + profissionalId
                + ",\"inicio\":\"" + inicio + "\"}";
    }

    /** Primeiro dia útil pelo menos N dias à frente (consultas não podem cair em fim de semana). */
    protected static LocalDate proximoDiaUtil(int diasMinimos) {
        LocalDate dia = LocalDate.now().plusDays(diasMinimos);
        while (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    protected static long idDe(ResultActions resposta) throws Exception {
        String json = resposta.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
