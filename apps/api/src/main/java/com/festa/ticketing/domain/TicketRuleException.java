package com.festa.ticketing.domain;

/**
 * Regra de ingresso violada. {@code conflict} = o estado atual não permite (409); caso contrário é
 * regra sobre os dados enviados (422).
 */
public class TicketRuleException extends RuntimeException {

	private final String code;
	private final boolean conflict;

	private TicketRuleException(String code, String message, boolean conflict) {
		super(message);
		this.code = code;
		this.conflict = conflict;
	}

	public static TicketRuleException conflict(String code, String message) {
		return new TicketRuleException(code, message, true);
	}

	public static TicketRuleException rule(String code, String message) {
		return new TicketRuleException(code, message, false);
	}

	public String getCode() {
		return code;
	}

	public boolean isConflict() {
		return conflict;
	}

}
