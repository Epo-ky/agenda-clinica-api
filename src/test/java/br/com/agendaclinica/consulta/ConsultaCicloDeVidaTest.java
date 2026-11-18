package br.com.agendaclinica.consulta;

import br.com.agendaclinica.common.BusinessException;
import br.com.agendaclinica.common.NotFoundException;
import br.com.agendaclinica.paciente.Paciente;
import br.com.agendaclinica.paciente.PacienteService;
import br.com.agendaclinica.profissional.Profissional;
import br.com.agendaclinica.profissional.ProfissionalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras 6 a 8. "Agora" fixo: quarta-feira, 18/11/2026, 10:00. */
@ExtendWith(MockitoExtension.class)
class ConsultaCicloDeVidaTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 18, 10, 0);

    @Mock
    ConsultaRepository repository;
    @Mock
    PacienteService pacienteService;
    @Mock
    ProfissionalService profissionalService;

    ConsultaService service;

    @BeforeEach
    void preparar() {
        Clock relogioFixo = Clock.fixed(AGORA.atZone(ZONA).toInstant(), ZONA);
        service = new ConsultaService(repository, pacienteService, profissionalService, relogioFixo);
        lenient().when(repository.save(any(Consulta.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Consulta consulta(StatusConsulta status, LocalDateTime inicio) {
        Paciente paciente = new Paciente();
        paciente.setId(1L);
        paciente.setNome("Maria Silva");
        Profissional profissional = new Profissional();
        profissional.setId(2L);
        profissional.setNome("Dra. Ana Lima");

        Consulta c = new Consulta();
        c.setPaciente(paciente);
        c.setProfissional(profissional);
        c.setInicio(inicio);
        c.setFim(inicio.plusMinutes(30));
        c.setStatus(status);
        when(repository.findById(10L)).thenReturn(Optional.of(c));
        return c;
    }

    private void recusa(Runnable acao, String trechoDaMensagem) {
        BusinessException ex = assertThrows(BusinessException.class, acao::run);
        assertTrue(ex.getMessage().contains(trechoDaMensagem),
                "Mensagem era: " + ex.getMessage());
        verify(repository, never()).save(any());
    }

    // ---------- confirmar ----------

    @Test
    void confirmaConsultaAgendada() {
        consulta(StatusConsulta.AGENDADA, AGORA.plusDays(2));

        assertEquals(StatusConsulta.CONFIRMADA, service.confirmar(10L).status());
    }

    @Test
    void registraAHoraDaAlteracaoNaPropriaResposta() {
        consulta(StatusConsulta.AGENDADA, AGORA.plusDays(2));

        assertEquals(AGORA, service.confirmar(10L).atualizadoEm());
    }

    @Test
    void naoConfirmaConsultaJaConfirmada() {
        consulta(StatusConsulta.CONFIRMADA, AGORA.plusDays(2));

        recusa(() -> service.confirmar(10L), "de CONFIRMADA para CONFIRMADA");
    }

    @Test
    void naoConfirmaConsultaCancelada() {
        consulta(StatusConsulta.CANCELADA, AGORA.plusDays(2));

        recusa(() -> service.confirmar(10L), "de CANCELADA para CONFIRMADA");
    }

    // ---------- realizar (regra 8) ----------

    @Test
    void realizaConsultaConfirmadaDepoisDoInicio() {
        consulta(StatusConsulta.CONFIRMADA, AGORA.minusHours(1));

        assertEquals(StatusConsulta.REALIZADA, service.realizar(10L).status());
    }

    @Test
    void realizaConsultaNoExatoMomentoDoInicio() {
        consulta(StatusConsulta.CONFIRMADA, AGORA);

        assertEquals(StatusConsulta.REALIZADA, service.realizar(10L).status());
    }

    @Test
    void naoRealizaAntesDoHorarioDeInicio() {
        consulta(StatusConsulta.CONFIRMADA, AGORA.plusMinutes(1));

        recusa(() -> service.realizar(10L), "depois do horário de início");
    }

    @Test
    void naoRealizaConsultaQueAindaNaoFoiConfirmada() {
        consulta(StatusConsulta.AGENDADA, AGORA.minusHours(1));

        recusa(() -> service.realizar(10L), "de AGENDADA para REALIZADA");
    }

    // ---------- cancelar (regra 7) ----------

    @Test
    void cancelaConsultaAgendadaGuardandoOMotivoSemEspacos() {
        consulta(StatusConsulta.AGENDADA, AGORA.plusDays(2));

        ConsultaResponse r = service.cancelar(10L, "  Paciente viajou ");

        assertEquals(StatusConsulta.CANCELADA, r.status());
        assertEquals("Paciente viajou", r.motivoCancelamento());
    }

    @Test
    void cancelaConsultaConfirmada() {
        consulta(StatusConsulta.CONFIRMADA, AGORA.plusDays(2));

        assertEquals(StatusConsulta.CANCELADA, service.cancelar(10L, "Imprevisto").status());
    }

    @Test
    void cancelaComExatamente24HorasDeAntecedencia() {
        consulta(StatusConsulta.AGENDADA, AGORA.plusHours(24));

        assertEquals(StatusConsulta.CANCELADA, service.cancelar(10L, "Imprevisto").status());
    }

    @Test
    void naoCancelaComMenosDe24HorasDeAntecedencia() {
        consulta(StatusConsulta.AGENDADA, AGORA.plusHours(24).minusMinutes(1));

        recusa(() -> service.cancelar(10L, "Imprevisto"), "24 horas de antecedência");
    }

    @Test
    void naoCancelaSemMotivo() {
        consulta(StatusConsulta.AGENDADA, AGORA.plusDays(2));

        recusa(() -> service.cancelar(10L, "   "), "motivo");
        recusa(() -> service.cancelar(10L, null), "motivo");
    }

    @Test
    void naoCancelaConsultaRealizada() {
        consulta(StatusConsulta.REALIZADA, AGORA.plusDays(2));

        recusa(() -> service.cancelar(10L, "Imprevisto"), "de REALIZADA para CANCELADA");
    }

    @Test
    void naoCancelaDuasVezes() {
        Consulta c = consulta(StatusConsulta.CANCELADA, AGORA.plusDays(2));
        c.setMotivoCancelamento("Primeiro motivo");

        recusa(() -> service.cancelar(10L, "Outro motivo"), "de CANCELADA para CANCELADA");
        assertEquals("Primeiro motivo", c.getMotivoCancelamento());
    }

    @Test
    void consultaNaoCanceladaNaoTemMotivo() {
        consulta(StatusConsulta.AGENDADA, AGORA.plusDays(2));

        assertNull(service.confirmar(10L).motivoCancelamento());
    }

    // ---------- inexistente ----------

    @Test
    void consultaInexistenteLancaNotFoundEmTodasAsAcoes() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.confirmar(99L));
        assertThrows(NotFoundException.class, () -> service.realizar(99L));
        assertThrows(NotFoundException.class, () -> service.cancelar(99L, "x"));
    }
}
