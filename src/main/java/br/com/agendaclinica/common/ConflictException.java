package br.com.agendaclinica.common;

/** Dado duplicado (CPF, e-mail): vira HTTP 409 no handler. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
