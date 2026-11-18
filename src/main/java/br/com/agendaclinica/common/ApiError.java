package br.com.agendaclinica.common;

import java.time.LocalDateTime;
import java.util.List;

/** Formato único de erro da API. */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho,
        List<CampoInvalido> campos
) {

    public record CampoInvalido(String campo, String mensagem) {
    }

    public static ApiError of(int status, String erro, String mensagem, String caminho) {
        return new ApiError(LocalDateTime.now(), status, erro, mensagem, caminho, List.of());
    }
}
