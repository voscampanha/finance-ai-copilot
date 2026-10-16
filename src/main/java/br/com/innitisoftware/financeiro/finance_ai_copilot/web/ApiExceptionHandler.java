package br.com.innitisoftware.financeiro.finance_ai_copilot.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.com.innitisoftware.financeiro.finance_ai_copilot.domain.exception.UsuarioNaoAutenticadoException;

/** Translates application and authorization exceptions into the API's JSON error format. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(JwtValidationException.class)
    public ResponseEntity<Map<String, String>> handleJwtValidation(JwtValidationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "erro", "token_invalido",
                        "detalhe", String.valueOf(exception.getMessage())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("erro", "acesso_negado"));
    }

    @ExceptionHandler(UsuarioNaoAutenticadoException.class)
    public ResponseEntity<Map<String, String>> handleUnauthenticatedUser(
            UsuarioNaoAutenticadoException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("erro", "usuario_nao_autenticado"));
    }
}
