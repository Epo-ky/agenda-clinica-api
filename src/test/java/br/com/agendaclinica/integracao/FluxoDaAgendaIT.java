package br.com.agendaclinica.integracao;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os cenários do spec: login, agendamento feliz, conflito de horário e acesso negado por perfil,
 * mais o ciclo de vida e os efeitos reais no banco (UNIQUE, exclusão lógica).
 */
class FluxoDaAgendaIT extends IntegracaoBase {

    private static final String CPF_A = "529.982.247-25";
    private static final String CPF_B = "111.444.777-35";

    // ---------- login ----------

    @Test
    void loginComSenhaCertaDevolveTokenEComSenhaErradaDevolve401() throws Exception {
        criarUsuario("ana@teste.com", "senha-da-ana-123", br.com.agendaclinica.usuario.Perfil.RECEPCAO);

        post("/auth/login", null, "{\"email\":\"ana@teste.com\",\"senha\":\"senha-da-ana-123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEmSegundos").value(7200));

        post("/auth/login", null, "{\"email\":\"ana@teste.com\",\"senha\":\"errada-errada\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"));
    }

    @Test
    void rotaProtegidaSemTokenDevolve401() throws Exception {
        get("/pacientes", null).andExpect(status().isUnauthorized());
        get("/consultas", null).andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioInativadoPerdeOAcessoImediatamente() throws Exception {
        String token = tokenRecepcao();
        get("/pacientes", token).andExpect(status().isOk());

        jdbc.update("update usuarios set ativo = false where email = 'recepcao@teste.com'");

        get("/pacientes", token).andExpect(status().isUnauthorized());
    }

    // ---------- agendamento ----------

    @Test
    void agendamentoFelizGravaNoBancoComFimCalculado() throws Exception {
        String admin = tokenAdmin();
        long paciente = criarPaciente(admin, "Maria Silva", CPF_A);
        long profissional = criarProfissional(admin, "Dra. Ana", 45);
        String dia = proximoDiaUtil(3).toString();

        post("/consultas", admin, corpoConsulta(paciente, profissional, dia + "T10:00:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.inicio").value(dia + "T10:00:00"))
                .andExpect(jsonPath("$.fim").value(dia + "T10:45:00"))
                .andExpect(jsonPath("$.pacienteNome").value("Maria Silva"));

        assertEquals(1, jdbc.queryForObject("select count(*) from consultas where status = 'AGENDADA'", Integer.class));
    }

    @Test
    void conflitoDeHorarioDevolve422ENaoGravaASegundaConsulta() throws Exception {
        String admin = tokenAdmin();
        long maria = criarPaciente(admin, "Maria Silva", CPF_A);
        long joao = criarPaciente(admin, "Joao Souza", CPF_B);
        long profissional = criarProfissional(admin, "Dra. Ana", 60);
        String dia = proximoDiaUtil(3).toString();

        post("/consultas", admin, corpoConsulta(maria, profissional, dia + "T10:00:00")).andExpect(status().isCreated());

        // sobreposição parcial: 10:30 cai dentro de 10:00-11:00
        post("/consultas", admin, corpoConsulta(joao, profissional, dia + "T10:30:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Profissional já tem consulta nesse horário"));

        // consulta colada (11:00) não conflita
        post("/consultas", admin, corpoConsulta(joao, profissional, dia + "T11:00:00")).andExpect(status().isCreated());

        assertEquals(2, jdbc.queryForObject("select count(*) from consultas", Integer.class));
    }

    @Test
    void pacienteNaoPodeTerDuasConsultasNoMesmoDiaComOMesmoProfissional() throws Exception {
        String admin = tokenAdmin();
        long maria = criarPaciente(admin, "Maria Silva", CPF_A);
        long profissional = criarProfissional(admin, "Dra. Ana", 30);
        String dia = proximoDiaUtil(3).toString();

        post("/consultas", admin, corpoConsulta(maria, profissional, dia + "T09:00:00")).andExpect(status().isCreated());

        post("/consultas", admin, corpoConsulta(maria, profissional, dia + "T15:00:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Paciente já tem consulta com este profissional neste dia"));
    }

    @Test
    void foraDoHorarioComercialEmFimDeSemanaEnoPassadoDevolve422() throws Exception {
        String admin = tokenAdmin();
        long maria = criarPaciente(admin, "Maria Silva", CPF_A);
        long profissional = criarProfissional(admin, "Dra. Ana", 30);
        LocalDate sabado = LocalDate.now().plusDays(10);
        while (sabado.getDayOfWeek() != java.time.DayOfWeek.SATURDAY) {
            sabado = sabado.plusDays(1);
        }
        String dia = proximoDiaUtil(3).toString();

        post("/consultas", admin, corpoConsulta(maria, profissional, sabado + "T10:00:00"))
                .andExpect(status().isUnprocessableEntity());
        post("/consultas", admin, corpoConsulta(maria, profissional, dia + "T07:30:00"))
                .andExpect(status().isUnprocessableEntity());
        post("/consultas", admin, corpoConsulta(maria, profissional, dia + "T17:45:00"))
                .andExpect(status().isUnprocessableEntity());
        post("/consultas", admin, corpoConsulta(maria, profissional, "2020-01-07T10:00:00"))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(0, jdbc.queryForObject("select count(*) from consultas", Integer.class));
    }

    @Test
    void pacienteInativadoNaoRecebeAgendamento() throws Exception {
        String admin = tokenAdmin();
        long maria = criarPaciente(admin, "Maria Silva", CPF_A);
        long profissional = criarProfissional(admin, "Dra. Ana", 30);
        delete("/pacientes/" + maria, admin).andExpect(status().isNoContent());

        post("/consultas", admin, corpoConsulta(maria, profissional, proximoDiaUtil(3) + "T10:00:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Paciente inativo não pode receber agendamento"));

        // exclusão lógica: a linha continua no banco
        assertEquals(false, jdbc.queryForObject("select ativo from pacientes where id = ?", Boolean.class, maria));
    }

    @Test
    void cpfDuplicadoDevolve409() throws Exception {
        String admin = tokenAdmin();
        criarPaciente(admin, "Maria Silva", CPF_A);

        post("/pacientes", admin,
                "{\"nome\":\"Outra Pessoa\",\"cpf\":\"52998224725\",\"dataNascimento\":\"1980-01-01\"}")
                .andExpect(status().isConflict());
    }

    // ---------- horários livres ----------

    @Test
    void horariosLivresNaoOferecemOHorarioJaAgendado() throws Exception {
        String admin = tokenAdmin();
        long maria = criarPaciente(admin, "Maria Silva", CPF_A);
        long profissional = criarProfissional(admin, "Dra. Ana", 60);
        String dia = proximoDiaUtil(3).toString();
        post("/consultas", admin, corpoConsulta(maria, profissional, dia + "T09:00:00")).andExpect(status().isCreated());

        get("/profissionais/" + profissional + "/horarios-livres?data=" + dia, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios.length()").value(9)) // 10 horários de 1h, menos o das 09:00
                .andExpect(jsonPath("$.horarios[0]").value("08:00:00"))
                .andExpect(jsonPath("$.horarios[1]").value("10:00:00"));
    }

    // ---------- ciclo de vida ----------

    @Test
    void cicloDeVidaConfirmarECancelarLiberaOHorario() throws Exception {
        String admin = tokenAdmin();
        long maria = criarPaciente(admin, "Maria Silva", CPF_A);
        long joao = criarPaciente(admin, "Joao Souza", CPF_B);
        long profissional = criarProfissional(admin, "Dra. Ana", 60);
        String dia = proximoDiaUtil(3).toString();
        long consulta = idDe(post("/consultas", admin, corpoConsulta(maria, profissional, dia + "T10:00:00")));

        patch("/consultas/" + consulta + "/confirmar", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"));

        // regra 8: ainda não chegou a hora
        patch("/consultas/" + consulta + "/realizar", admin, null).andExpect(status().isUnprocessableEntity());

        patch("/consultas/" + consulta + "/cancelar", admin, "{\"motivo\":\"Paciente viajou\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelamento").value("Paciente viajou"));

        // regra 6: cancelada é estado final
        patch("/consultas/" + consulta + "/confirmar", admin, null).andExpect(status().isUnprocessableEntity());

        // o horário cancelado volta a ficar livre para outro paciente
        post("/consultas", admin, corpoConsulta(joao, profissional, dia + "T10:00:00")).andExpect(status().isCreated());
    }

    @Test
    void cancelamentoComMenosDe24HorasEhRecusadoENaoAlteraOBanco() throws Exception {
        String admin = tokenAdmin();
        long maria = criarPaciente(admin, "Maria Silva", CPF_A);
        long profissional = criarProfissional(admin, "Dra. Ana", 30);
        // Cenário: consulta daqui a 5 horas (menos de 24h). A API não deixaria agendar para hoje fora do
        // expediente, então gravamos direto no banco, só para montar o cenário.
        jdbc.update("insert into consultas (paciente_id, profissional_id, inicio, fim, status, criado_em) "
                        + "values (?, ?, now() + interval '5 hours', now() + interval '5 hours 30 minutes', 'AGENDADA', now())",
                maria, profissional);
        long consulta = jdbc.queryForObject("select id from consultas", Long.class);

        patch("/consultas/" + consulta + "/cancelar", admin, "{\"motivo\":\"Imprevisto\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("O cancelamento exige pelo menos 24 horas de antecedência"));

        assertEquals("AGENDADA", jdbc.queryForObject("select status from consultas where id = ?", String.class, consulta));
    }

    // ---------- perfis ----------

    @Test
    void recepcaoNaoPodeApagarPacienteNemCadastrarProfissionalNemCriarUsuario() throws Exception {
        String admin = tokenAdmin();
        long paciente = criarPaciente(admin, "Maria Silva", CPF_A);
        String recepcao = tokenRecepcao();

        delete("/pacientes/" + paciente, recepcao).andExpect(status().isForbidden());
        post("/profissionais", recepcao,
                "{\"nome\":\"X\",\"registroConselho\":\"CRM-9\",\"especialidade\":\"PEDIATRIA\"}")
                .andExpect(status().isForbidden());
        post("/usuarios", recepcao,
                "{\"email\":\"novo@teste.com\",\"senha\":\"senha-forte-123\",\"perfil\":\"ADMIN\"}")
                .andExpect(status().isForbidden());

        // nada mudou no banco
        assertEquals(true, jdbc.queryForObject("select ativo from pacientes where id = ?", Boolean.class, paciente));
        assertEquals(0, jdbc.queryForObject("select count(*) from profissionais", Integer.class));
    }

    @Test
    void recepcaoPodeCadastrarPacienteEAgendarConsulta() throws Exception {
        String admin = tokenAdmin();
        long profissional = criarProfissional(admin, "Dra. Ana", 30);
        String recepcao = tokenRecepcao();

        long paciente = criarPaciente(recepcao, "Maria Silva", CPF_A);
        post("/consultas", recepcao, corpoConsulta(paciente, profissional, proximoDiaUtil(3) + "T10:00:00"))
                .andExpect(status().isCreated());
    }

    @Test
    void adminCriaUsuarioQueConsegueLogarESenhaFicaGuardadaSoComoHash() throws Exception {
        String admin = tokenAdmin();

        post("/usuarios", admin,
                "{\"email\":\"Nova@Teste.com\",\"senha\":\"senha-forte-123\",\"perfil\":\"RECEPCAO\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nova@teste.com"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());

        login("nova@teste.com", "senha-forte-123"); // loga com o e-mail normalizado

        String hash = jdbc.queryForObject("select senha_hash from usuarios where email = 'nova@teste.com'", String.class);
        assertEquals(true, hash != null && hash.startsWith("$2"));
    }
}
