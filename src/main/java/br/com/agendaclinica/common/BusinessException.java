package br.com.agendaclinica.common;

/** Regra de negócio violada (horário inválido, conflito, transição de status...): vira HTTP 422. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
