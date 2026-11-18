package br.com.agendaclinica.consulta;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record HorariosLivresResponse(
        Long profissionalId,
        LocalDate data,
        int duracaoConsultaMin,
        List<LocalTime> horarios
) {
}
