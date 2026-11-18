package br.com.agendaclinica.consulta;

import br.com.agendaclinica.common.BusinessException;
import br.com.agendaclinica.common.NotFoundException;
import br.com.agendaclinica.paciente.Paciente;
import br.com.agendaclinica.paciente.PacienteService;
import br.com.agendaclinica.profissional.Especialidade;
import br.com.agendaclinica.profissional.Profissional;
import br.com.agendaclinica.profissional.ProfissionalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Agora" é fixo: quarta-feira, 18/11/2026, 10:00.
 * Quinta = 19/11, sexta = 20/11, sábado = 21/11, domingo = 22/11.
 */
@ExtendWith(MockitoExtension.class)
class ConsultaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 18, 10, 0);
    private static final LocalDate QUINTA = LocalDate.of(2026, 11, 19);
    private static final LocalDate SABADO = LocalDate.of(2026, 11, 21);

    @Mock
    ConsultaRepository repository;
    @Mock
    PacienteService pacienteService;
    @Mock
    ProfissionalService profissionalService;

    ConsultaService service;
    Paciente paciente;
    Profissional profissional;

    @BeforeEach
    void preparar() {
        Clock relogioFixo = Clock.fixed(AGORA.atZone(ZONA).toInstant(), ZONA);
        service = new ConsultaService(repository, pacienteService, profissionalService, relogioFixo);

        paciente = new Paciente();
        paciente.setId(1L);
        paciente.setNome("Maria Silva");

        profissional = new Profissional();
        profissional.setId(2L);
        profissional.setNome("Dra. Ana Lima");
        profissional.setEspecialidade(Especialidade.CARDIOLOGIA);
        profissional.setDuracaoConsultaMin(30);

        lenient().when(pacienteService.buscar(1L)).thenReturn(paciente);
        lenient().when(profissionalService.buscar(2L)).thenReturn(profissional);
        lenient().when(repository.save(any(Consulta.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ConsultaResponse agendar(LocalDateTime inicio) {
        return service.agendar(new ConsultaRequest(1L, 2L, inicio));
    }

    private BusinessException recusa(LocalDateTime inicio) {
        BusinessException ex = assertThrows(BusinessException.class, () -> agendar(inicio));
        verify(repository, never()).save(any());
        return ex;
    }

    // ---------- agendamento feliz ----------

    @Test
    void agendaConsultaCalculandoFimPelaDuracaoDoProfissional() {
        profissional.setDuracaoConsultaMin(45);

        ConsultaResponse r = agendar(QUINTA.atTime(9, 0));

        assertEquals(QUINTA.atTime(9, 0), r.inicio());
        assertEquals(QUINTA.atTime(9, 45), r.fim());
        assertEquals(StatusConsulta.AGENDADA, r.status());
        assertEquals("Maria Silva", r.pacienteNome());
        assertEquals("Dra. Ana Lima", r.profissionalNome());
    }

    @Test
    void aceitaConsultaQueTerminaExatamenteAsDezoitoHoras() {
        assertEquals(QUINTA.atTime(18, 0), agendar(QUINTA.atTime(17, 30)).fim());
    }

    @Test
    void aceitaConsultaComExatamenteUmaHoraDeAntecedencia() {
        assertEquals(StatusConsulta.AGENDADA, agendar(AGORA.plusHours(1)).status());
    }

    // ---------- regra 1: horário comercial ----------

    @Test
    void recusaSabado() {
        assertTrue(recusa(SABADO.atTime(10, 0)).getMessage().contains("segunda a sexta"));
    }

    @Test
    void recusaDomingo() {
        recusa(LocalDate.of(2026, 11, 22).atTime(10, 0));
    }

    @Test
    void recusaAntesDasOitoHoras() {
        assertTrue(recusa(QUINTA.atTime(7, 30)).getMessage().contains("08:00"));
    }

    @Test
    void recusaConsultaQueTerminaDepoisDasDezoitoHoras() {
        // 17:45 + 30 min = 18:15
        assertTrue(recusa(QUINTA.atTime(17, 45)).getMessage().contains("18:00"));
    }

    @Test
    void recusaConsultaQueComecaDepoisDoExpediente() {
        recusa(QUINTA.atTime(19, 0));
    }

    // ---------- regra 2: antecedência ----------

    @Test
    void recusaConsultaNoPassado() {
        assertTrue(recusa(LocalDate.of(2026, 11, 17).atTime(10, 0)).getMessage().contains("passado"));
    }

    @Test
    void recusaConsultaComMenosDeUmaHoraDeAntecedencia() {
        assertTrue(recusa(AGORA.plusMinutes(30)).getMessage().contains("1 hora de antecedência"));
    }

    // ---------- regra 3: conflito do profissional ----------

    @Test
    void recusaQuandoProfissionalJaTemConsultaNoHorario() {
        when(repository.existeConflitoDoProfissional(2L, QUINTA.atTime(9, 0), QUINTA.atTime(9, 30)))
                .thenReturn(true);

        assertEquals("Profissional já tem consulta nesse horário", recusa(QUINTA.atTime(9, 0)).getMessage());
    }

    // ---------- regra 4: paciente repetido no dia ----------

    @Test
    void recusaQuandoPacienteJaTemConsultaNoDiaComOMesmoProfissional() {
        when(repository.existeConsultaDoPacienteNoDia(1L, 2L, QUINTA.atStartOfDay(), QUINTA.plusDays(1).atStartOfDay()))
                .thenReturn(true);

        assertTrue(recusa(QUINTA.atTime(15, 0)).getMessage().contains("neste dia"));
    }

    // ---------- regra 5: inativos ----------

    @Test
    void recusaPacienteInativo() {
        paciente.setAtivo(false);

        assertTrue(recusa(QUINTA.atTime(9, 0)).getMessage().contains("Paciente inativo"));
    }

    @Test
    void recusaProfissionalInativo() {
        profissional.setAtivo(false);

        assertTrue(recusa(QUINTA.atTime(9, 0)).getMessage().contains("Profissional inativo"));
    }

    @Test
    void pacienteInexistenteLancaNotFound() {
        when(pacienteService.buscar(99L)).thenThrow(new NotFoundException("Paciente não encontrado: 99"));

        assertThrows(NotFoundException.class, () -> service.agendar(new ConsultaRequest(99L, 2L, QUINTA.atTime(9, 0))));
        verify(repository, never()).save(any());
    }

    // ---------- horários livres ----------

    @Test
    void diaUtilSemConsultasTemTodosOsHorarios() {
        HorariosLivresResponse r = service.horariosLivres(2L, QUINTA);

        assertEquals(20, r.horarios().size());
        assertEquals(LocalTime.of(8, 0), r.horarios().get(0));
        assertEquals(LocalTime.of(17, 30), r.horarios().get(19));
    }

    @Test
    void fimDeSemanaNaoTemHorarios() {
        assertTrue(service.horariosLivres(2L, SABADO).horarios().isEmpty());
    }

    @Test
    void profissionalInativoNaoTemHorarios() {
        profissional.setAtivo(false);

        assertTrue(service.horariosLivres(2L, QUINTA).horarios().isEmpty());
    }

    @Test
    void horariosOcupadosSaemDaLista() {
        when(repository.findAgendaDoDia(anyLong(), any(), any())).thenReturn(List.of(
                consulta(QUINTA.atTime(9, 0), QUINTA.atTime(9, 30)),
                consulta(QUINTA.atTime(10, 0), QUINTA.atTime(11, 0))));

        List<LocalTime> livres = service.horariosLivres(2L, QUINTA).horarios();

        assertFalse(livres.contains(LocalTime.of(9, 0)));
        assertFalse(livres.contains(LocalTime.of(10, 0)));
        assertFalse(livres.contains(LocalTime.of(10, 30)));
        assertTrue(livres.contains(LocalTime.of(9, 30)));
        assertTrue(livres.contains(LocalTime.of(11, 0)));
        assertEquals(17, livres.size());
    }

    @Test
    void hojeNaoOferecePassadoNemMenosDeUmaHoraDeAntecedencia() {
        List<LocalTime> livres = service.horariosLivres(2L, AGORA.toLocalDate()).horarios();

        assertEquals(LocalTime.of(11, 0), livres.get(0));
        assertEquals(14, livres.size());
    }

    @Test
    void duracaoMaiorGeraMenosHorariosEUltimoTerminaAteAsDezoito() {
        profissional.setDuracaoConsultaMin(45);

        List<LocalTime> livres = service.horariosLivres(2L, QUINTA).horarios();

        assertEquals(13, livres.size());
        assertEquals(LocalTime.of(17, 0), livres.get(12)); // 17:00 + 45 min = 17:45
    }

    private static Consulta consulta(LocalDateTime inicio, LocalDateTime fim) {
        Consulta c = new Consulta();
        c.setInicio(inicio);
        c.setFim(fim);
        return c;
    }
}
