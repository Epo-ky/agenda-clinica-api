package br.com.agendaclinica.consulta;

import java.time.LocalDateTime;

public record ConsultaResponse(
        Long id,
        Long pacienteId,
        String pacienteNome,
        Long profissionalId,
        String profissionalNome,
        LocalDateTime inicio,
        LocalDateTime fim,
        StatusConsulta status,
        String motivoCancelamento,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {

    /** Precisa ser chamado dentro da transação do service, pois acessa as relações LAZY. */
    public static ConsultaResponse from(Consulta c) {
        return new ConsultaResponse(
                c.getId(),
                c.getPaciente().getId(), c.getPaciente().getNome(),
                c.getProfissional().getId(), c.getProfissional().getNome(),
                c.getInicio(), c.getFim(), c.getStatus(), c.getMotivoCancelamento(),
                c.getCriadoEm(), c.getAtualizadoEm());
    }
}
