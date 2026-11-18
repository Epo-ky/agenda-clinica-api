package br.com.agendaclinica.consulta;

import br.com.agendaclinica.common.BusinessException;
import br.com.agendaclinica.common.NotFoundException;
import br.com.agendaclinica.paciente.Paciente;
import br.com.agendaclinica.paciente.PacienteService;
import br.com.agendaclinica.profissional.Profissional;
import br.com.agendaclinica.profissional.ProfissionalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Regras de agendamento. Cada regra quebrada vira BusinessException (HTTP 422) com mensagem clara. */
@Service
public class ConsultaService {

    static final LocalTime ABERTURA = LocalTime.of(8, 0);
    static final LocalTime FECHAMENTO = LocalTime.of(18, 0);
    static final Duration ANTECEDENCIA_MINIMA = Duration.ofHours(1);
    static final Duration ANTECEDENCIA_CANCELAMENTO = Duration.ofHours(24);

    private final ConsultaRepository repository;
    private final PacienteService pacienteService;
    private final ProfissionalService profissionalService;
    private final Clock clock;

    public ConsultaService(ConsultaRepository repository,
                           PacienteService pacienteService,
                           ProfissionalService profissionalService,
                           Clock clock) {
        this.repository = repository;
        this.pacienteService = pacienteService;
        this.profissionalService = profissionalService;
        this.clock = clock;
    }

    @Transactional
    public ConsultaResponse agendar(ConsultaRequest req) {
        Paciente paciente = pacienteService.buscar(req.pacienteId());
        Profissional profissional = profissionalService.buscar(req.profissionalId());

        // Regra 5
        if (!paciente.isAtivo()) {
            throw new BusinessException("Paciente inativo não pode receber agendamento");
        }
        if (!profissional.isAtivo()) {
            throw new BusinessException("Profissional inativo não pode receber agendamento");
        }

        LocalDateTime inicio = req.inicio();
        LocalDateTime fim = inicio.plusMinutes(profissional.getDuracaoConsultaMin());

        validarHorarioComercial(inicio, fim); // Regra 1
        validarAntecedencia(inicio);          // Regra 2

        // Regra 3
        if (repository.existeConflitoDoProfissional(profissional.getId(), inicio, fim)) {
            throw new BusinessException("Profissional já tem consulta nesse horário");
        }
        // Regra 4
        LocalDateTime diaInicio = inicio.toLocalDate().atStartOfDay();
        if (repository.existeConsultaDoPacienteNoDia(paciente.getId(), profissional.getId(),
                diaInicio, diaInicio.plusDays(1))) {
            throw new BusinessException("Paciente já tem consulta com este profissional neste dia");
        }

        Consulta consulta = new Consulta();
        consulta.setPaciente(paciente);
        consulta.setProfissional(profissional);
        consulta.setInicio(inicio);
        consulta.setFim(fim);
        consulta.setStatus(StatusConsulta.AGENDADA);
        return ConsultaResponse.from(repository.save(consulta));
    }

    @Transactional
    public ConsultaResponse confirmar(Long id) {
        Consulta consulta = buscar(id);
        exigirTransicao(consulta, StatusConsulta.CONFIRMADA);
        consulta.setStatus(StatusConsulta.CONFIRMADA);
        return salvarAlteracao(consulta);
    }

    @Transactional
    public ConsultaResponse realizar(Long id) {
        Consulta consulta = buscar(id);
        exigirTransicao(consulta, StatusConsulta.REALIZADA);
        // Regra 8: só depois do horário de início
        if (LocalDateTime.now(clock).isBefore(consulta.getInicio())) {
            throw new BusinessException("A consulta só pode ser marcada como realizada depois do horário de início");
        }
        consulta.setStatus(StatusConsulta.REALIZADA);
        return salvarAlteracao(consulta);
    }

    @Transactional
    public ConsultaResponse cancelar(Long id, String motivo) {
        Consulta consulta = buscar(id);
        exigirTransicao(consulta, StatusConsulta.CANCELADA);
        // Regra 7: motivo obrigatório e pelo menos 24 horas de antecedência
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("O cancelamento exige um motivo");
        }
        if (LocalDateTime.now(clock).plus(ANTECEDENCIA_CANCELAMENTO).isAfter(consulta.getInicio())) {
            throw new BusinessException("O cancelamento exige pelo menos 24 horas de antecedência");
        }
        consulta.setStatus(StatusConsulta.CANCELADA);
        consulta.setMotivoCancelamento(motivo.trim());
        return salvarAlteracao(consulta);
    }

    private ConsultaResponse salvarAlteracao(Consulta consulta) {
        consulta.setAtualizadoEm(LocalDateTime.now(clock));
        return ConsultaResponse.from(repository.save(consulta));
    }

    private Consulta buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Consulta não encontrada: " + id));
    }

    private void exigirTransicao(Consulta consulta, StatusConsulta destino) {
        if (!consulta.getStatus().podeIrPara(destino)) {
            throw new BusinessException("Não é possível passar a consulta de "
                    + consulta.getStatus() + " para " + destino);
        }
    }

    @Transactional(readOnly = true)
    public Page<ConsultaResponse> listar(Long profissionalId, Long pacienteId, LocalDate data,
                                         StatusConsulta status, Pageable pageable) {
        return repository.findAll(ConsultaSpecs.filtrar(profissionalId, pacienteId, data, status), pageable)
                .map(ConsultaResponse::from);
    }

    @Transactional(readOnly = true)
    public HorariosLivresResponse horariosLivres(Long profissionalId, LocalDate data) {
        Profissional profissional = profissionalService.buscar(profissionalId);
        int duracao = profissional.getDuracaoConsultaMin();

        if (!profissional.isAtivo() || ehFimDeSemana(data)) {
            return new HorariosLivresResponse(profissionalId, data, duracao, List.of());
        }

        LocalDateTime diaInicio = data.atStartOfDay();
        List<Consulta> ocupadas = repository.findAgendaDoDia(profissionalId, diaInicio, diaInicio.plusDays(1));
        LocalDateTime limiteAntecedencia = LocalDateTime.now(clock).plus(ANTECEDENCIA_MINIMA);

        List<LocalTime> livres = new ArrayList<>();
        LocalDateTime slot = data.atTime(ABERTURA);
        LocalDateTime fechamento = data.atTime(FECHAMENTO);
        while (!slot.plusMinutes(duracao).isAfter(fechamento)) {
            LocalDateTime inicioSlot = slot;
            LocalDateTime fimSlot = slot.plusMinutes(duracao);
            boolean antecedenciaOk = !inicioSlot.isBefore(limiteAntecedencia);
            boolean livre = ocupadas.stream()
                    .noneMatch(c -> c.getInicio().isBefore(fimSlot) && c.getFim().isAfter(inicioSlot));
            if (antecedenciaOk && livre) {
                livres.add(inicioSlot.toLocalTime());
            }
            slot = fimSlot;
        }
        return new HorariosLivresResponse(profissionalId, data, duracao, livres);
    }

    private void validarHorarioComercial(LocalDateTime inicio, LocalDateTime fim) {
        if (ehFimDeSemana(inicio.toLocalDate())) {
            throw new BusinessException("Consultas só podem ser marcadas de segunda a sexta");
        }
        boolean mesmoDia = fim.toLocalDate().equals(inicio.toLocalDate());
        if (inicio.toLocalTime().isBefore(ABERTURA) || !mesmoDia || fim.toLocalTime().isAfter(FECHAMENTO)) {
            throw new BusinessException("Consultas só podem ser marcadas entre 08:00 e 18:00, "
                    + "e precisam terminar até as 18:00");
        }
    }

    private void validarAntecedencia(LocalDateTime inicio) {
        LocalDateTime agora = LocalDateTime.now(clock);
        if (inicio.isBefore(agora)) {
            throw new BusinessException("Não é possível marcar consulta no passado");
        }
        if (inicio.isBefore(agora.plus(ANTECEDENCIA_MINIMA))) {
            throw new BusinessException("A consulta precisa ser marcada com pelo menos 1 hora de antecedência");
        }
    }

    private static boolean ehFimDeSemana(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
    }
}
