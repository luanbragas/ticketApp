package com.festa.event.domain;

import com.festa.event.api.EventStatus;

import java.util.List;

/**
 * Regra do evento violada. {@code conflict} = transição de estado inválida (409);
 * caso contrário é regra de negócio sobre os dados (422).
 */
public class EventRuleException extends RuntimeException {

	private final String code;
	private final boolean conflict;
	private final List<String> missing;

	private EventRuleException(String code, String message, boolean conflict, List<String> missing) {
		super(message);
		this.code = code;
		this.conflict = conflict;
		this.missing = missing;
	}

	static EventRuleException invalidTransition(EventStatus from, String action) {
		return new EventRuleException("invalid-event-status",
			"Não dá para " + action + " um evento " + label(from) + ".", true, List.of());
	}

	static EventRuleException rule(String code, String message) {
		return new EventRuleException(code, message, false, List.of());
	}

	static EventRuleException notReady(List<String> missing) {
		return new EventRuleException("event-not-ready", "Falta completar o evento antes de publicar.", false,
			List.copyOf(missing));
	}

	public String getCode() {
		return code;
	}

	public boolean isConflict() {
		return conflict;
	}

	/** Campos que faltam para publicar (nomes da API). */
	public List<String> getMissing() {
		return missing;
	}

	private static String label(EventStatus status) {
		return switch (status) {
			case DRAFT -> "em rascunho";
			case PUBLISHED -> "publicado";
			case ENDED -> "encerrado";
			case CANCELLED -> "cancelado";
		};
	}

}
