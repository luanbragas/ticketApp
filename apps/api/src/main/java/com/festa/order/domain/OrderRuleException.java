package com.festa.order.domain;

import java.util.Map;

/**
 * Regra do pedido violada. {@code conflict} = estado ou estoque não permite (409); caso contrário é
 * regra sobre os dados enviados (422). {@code properties} vão como extras no Problem Details.
 */
public class OrderRuleException extends RuntimeException {

	private final String code;
	private final boolean conflict;
	private final Map<String, Object> properties;

	private OrderRuleException(String code, String message, boolean conflict, Map<String, Object> properties) {
		super(message);
		this.code = code;
		this.conflict = conflict;
		this.properties = Map.copyOf(properties);
	}

	public static OrderRuleException conflict(String code, String message) {
		return new OrderRuleException(code, message, true, Map.of());
	}

	public static OrderRuleException conflict(String code, String message, Map<String, Object> properties) {
		return new OrderRuleException(code, message, true, properties);
	}

	public static OrderRuleException rule(String code, String message) {
		return new OrderRuleException(code, message, false, Map.of());
	}

	public static OrderRuleException rule(String code, String message, Map<String, Object> properties) {
		return new OrderRuleException(code, message, false, properties);
	}

	public String getCode() {
		return code;
	}

	public boolean isConflict() {
		return conflict;
	}

	public Map<String, Object> getProperties() {
		return properties;
	}

}
