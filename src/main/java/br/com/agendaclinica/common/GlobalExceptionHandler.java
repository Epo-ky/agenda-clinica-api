package br.com.agendaclinica.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Captura as exceções de toda a API e devolve sempre o mesmo formato (ApiError).
 * Faz o papel de um middleware/exception filter de erros no ASP.NET Core.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.CampoInvalido(e.getField(), e.getDefaultMessage()))
                .toList();
        ApiError body = new ApiError(LocalDateTime.now(), 400, "Requisição inválida",
                "Um ou mais campos são inválidos", req.getRequestURI(), campos);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "Corpo ou parâmetro da requisição em formato inválido", req);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Não encontrado", ex.getMessage(), req);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflito", ex.getMessage(), req);
    }

    /** Rede de segurança: duas requisições simultâneas com o mesmo CPF passam do service e caem no UNIQUE do banco. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violação de integridade em {}", req.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT, "Conflito", "Registro duplicado ou inconsistente", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "O parâmetro '" + ex.getParameterName() + "' é obrigatório", req);
    }

    /**
     * Qualquer outra falha: 500 sem vazar detalhe interno para o cliente.
     * Exceções do próprio Spring (rota inexistente, método ou tipo de conteúdo não aceito) implementam
     * ErrorResponse e já carregam o status certo (404, 405, 415...): preservamos esse status.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse spring) {
            HttpStatus status = HttpStatus.valueOf(spring.getStatusCode().value());
            return build(status, mensagemPadrao(status), mensagemPadrao(status), req);
        }
        log.error("Erro inesperado em {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Ocorreu um erro inesperado", req);
    }

    private static String mensagemPadrao(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Recurso não encontrado";
            case METHOD_NOT_ALLOWED -> "Método HTTP não permitido para este recurso";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de conteúdo não suportado (use application/json)";
            case NOT_ACCEPTABLE -> "Formato de resposta não disponível";
            case BAD_REQUEST -> "Requisição inválida";
            default -> status.getReasonPhrase();
        };
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String erro, String mensagem, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), erro, mensagem, req.getRequestURI()));
    }
}
