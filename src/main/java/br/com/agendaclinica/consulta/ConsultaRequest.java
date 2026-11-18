package br.com.agendaclinica.consulta;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** O fim da consulta não vem do cliente: é início + duração do profissional. */
public record ConsultaRequest(
        @NotNull(message = "O paciente é obrigatório")
        Long pacienteId,

        @NotNull(message = "O profissional é obrigatório")
        Long profissionalId,

        @NotNull(message = "O horário de início é obrigatório")
        LocalDateTime inicio
) {
}
