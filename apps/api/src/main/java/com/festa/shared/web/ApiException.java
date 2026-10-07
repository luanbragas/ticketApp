package com.festa.shared.web;

import org.springframework.http.HttpStatus;

/**
 * Erro de negócio exposto como Problem Details (RFC 9457).
 * {@code code} vira o sufixo do {@code type}: {@code https://festa.com/errors/{code}}.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final String title;

	public ApiException(HttpStatus status, String code, String title, String detail) {
		super(detail);
		this.status = status;
		this.code = code;
		this.title = title;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public String getTitle() {
		return title;
	}

}
