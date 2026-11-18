package br.com.agendaclinica.paciente;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** O que a API devolve. A entidade nunca sai direto. */
public record PacienteResponse(
        Long id,
        String nome,
        String cpf,
        LocalDate dataNascimento,
        String telefone,
        String email,
        boolean ativo,
        LocalDateTime criadoEm
) {

    public static PacienteResponse from(Paciente p) {
        return new PacienteResponse(
                p.getId(), p.getNome(), p.getCpf(), p.getDataNascimento(),
                p.getTelefone(), p.getEmail(), p.isAtivo(), p.getCriadoEm());
    }
}
