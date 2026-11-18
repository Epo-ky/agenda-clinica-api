package br.com.agendaclinica.profissional;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfissionalRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 160, message = "O nome deve ter no máximo 160 caracteres")
        String nome,

        @NotBlank(message = "O registro no conselho é obrigatório")
        @Size(max = 30, message = "O registro deve ter no máximo 30 caracteres")
        String registroConselho,

        @NotNull(message = "A especialidade é obrigatória")
        Especialidade especialidade,

        // Opcional: se vier nulo, o service usa 30 minutos.
        @Min(value = 10, message = "A duração mínima é de 10 minutos")
        @Max(value = 240, message = "A duração máxima é de 240 minutos")
        Integer duracaoConsultaMin
) {
}
