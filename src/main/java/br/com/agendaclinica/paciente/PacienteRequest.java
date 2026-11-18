package br.com.agendaclinica.paciente;

import br.com.agendaclinica.common.Cpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** O que o cliente envia para cadastrar ou atualizar. Record = DTO imutável. */
public record PacienteRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 160, message = "O nome deve ter no máximo 160 caracteres")
        String nome,

        @NotBlank(message = "O CPF é obrigatório")
        @Cpf
        String cpf,

        @NotNull(message = "A data de nascimento é obrigatória")
        @Past(message = "A data de nascimento deve estar no passado")
        LocalDate dataNascimento,

        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String telefone,

        @Email(message = "E-mail inválido")
        @Size(max = 160, message = "O e-mail deve ter no máximo 160 caracteres")
        String email
) {
}
