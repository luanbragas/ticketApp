package com.festa.shared.web;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Erro de negócio exposto como Problem Details (RFC 9457).
 * {@code code} vira o sufixo do {@code type}: {@code https://festa.com/errors/{code}}.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final String title;
	private final Map<String, Object> properties = new LinkedHashMap<>();

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

	/** Campo extra no Problem Details (ex.: {@code missing} com o que falta para publicar). */
	public ApiException withProperty(String name, Object value) {
		properties.put(name, value);
		return this;
	}

	public Map<String, Object> getProperties() {
		return Map.copyOf(properties);
	}

}
