package br.com.agendaclinica.config;

import br.com.agendaclinica.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Erros de segurança acontecem no filtro, ANTES de chegar a qualquer controller, então o
 * GlobalExceptionHandler não os enxerga. Esta classe devolve o mesmo formato de erro (ApiError).
 */
@Component
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Sem token, ou token inválido/expirado: 401. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        escrever(response, request, HttpStatus.UNAUTHORIZED, "Não autenticado",
                "Autenticação necessária: envie um token válido no cabeçalho Authorization");
    }

    /** Autenticado, mas sem o perfil necessário: 403. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        escrever(response, request, HttpStatus.FORBIDDEN, "Acesso negado",
                "Seu perfil não tem permissão para esta operação");
    }

    private void escrever(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
                          String erro, String mensagem) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiError.of(status.value(), erro, mensagem, request.getRequestURI()));
    }
}
