package br.com.agendaclinica.common;

/** Recurso não encontrado: vira HTTP 404 no handler. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
