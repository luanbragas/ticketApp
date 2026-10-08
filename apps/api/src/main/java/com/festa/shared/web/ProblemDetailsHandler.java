package com.festa.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;

/** Converte exceções em Problem Details (docs/BACKEND.md §Convenções de API). Nunca expõe detalhes internos. */
@RestControllerAdvice
public class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

	public record FieldError(String field, String message) {
	}

	static ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("https://festa.com/errors/" + code));
		problem.setTitle(title);
		return problem;
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
		ProblemDetail problem = problem(ex.getStatus(), ex.getCode(), ex.getTitle(), ex.getMessage());
		ex.getProperties().forEach(problem::setProperty);
		return of(problem);
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
		if (ex instanceof BadCredentialsException) {
			return of(problem(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Credenciais inválidas",
				"E-mail ou senha incorretos."));
		}
		return of(problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Não autenticado", "Faça login para continuar."));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
		if (ex instanceof CsrfException) {
			return of(problem(HttpStatus.FORBIDDEN, "csrf", "Token CSRF inválido",
				"Sua sessão de segurança expirou. Recarregue a página e tente de novo."));
		}
		return of(problem(HttpStatus.FORBIDDEN, "forbidden", "Acesso negado",
			"Você não tem permissão para esta ação."));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Erro inesperado", ex);
		return of(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Erro interno",
			"Algo deu errado. Tente novamente em instantes."));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
			.map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
			.toList();
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation", "Dados inválidos",
			"Confira os campos destacados.");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "malformed-request",
			"Requisição inválida", "O corpo da requisição está malformado ou tem campos desconhecidos."));
	}

	/** Id malformado na URL (ex.: UUID inválido) é tratado como recurso inexistente. */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound());
	}

	@Override
	protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound());
	}

	private static ProblemDetail notFound() {
		return problem(HttpStatus.NOT_FOUND, "not-found", "Não encontrado", "O recurso solicitado não existe.");
	}

	private static ResponseEntity<ProblemDetail> of(ProblemDetail problem) {
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

}
