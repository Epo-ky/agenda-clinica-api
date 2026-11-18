package br.com.agendaclinica.common;

/** Credenciais inválidas no login: vira HTTP 401 no handler. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
